package cn.blockforge.generated.coordinatepointer.compat;

import cn.blockforge.generated.coordinatepointer.CoordinatePointerMod;
import cn.blockforge.generated.coordinatepointer.ModItems;
import cn.blockforge.generated.coordinatepointer.data.PointerData;
import cn.blockforge.generated.coordinatepointer.data.TargetEntry;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 和「机械动力：航空学 / Create Simulated」的导航台联动。
 *
 * <p>导航台只认"身上带 {@code simulated:target} 数据的物品"。这个数据里装的是一个
 * {@code NavigationTarget} 对象，导航台每 tick 问它一次"目标点在哪"，然后：
 * <ul>
 *   <li>用它算红石信号强度；</li>
 *   <li>把它转成方块朝向下的角度，让摆在上面的物品跟着转 —— 但物品得先在标签
 *       {@code simulated:rotate_with_nav_arrow} 里，导航台才肯把它转起来。
 *       我们的 data 包里有这个标签文件（{@code data/simulated/tags/item/rotate_with_nav_arrow.json}）。</li>
 * </ul>
 *
 * <p>所以这里做两件事：
 * <ol>
 *   <li>往航空学的 {@code simulated:navigation_target} 注册表里放一个"目标 = 指针当前坐标"的对象
 *       （这个数据组件是吃注册表 id 的，不进注册表就没法存档、没法同步）；</li>
 *   <li>把 {@code simulated:target} 设成坐标指针的默认数据组件，导航台才肯收这个物品。</li>
 * </ol>
 *
 * <p>为了"航空学更新了还能用"，这里不直接引用它的类，全部靠反射，而且：
 * <ul>
 *   <li>找类：按几个历史上出现过的全名依次试，认"方法长什么样"而不是认名字；</li>
 *   <li>找注册表 / 组件：按命名空间 {@code simulated} + 名字里带 {@code target} 来认，改路径名也不怕；</li>
 *   <li>认方法：接口里的方法按返回类型决定给什么值（要位置就给位置，要布尔就给"有没有目标"），
 *       有默认实现的一律走它自己的默认实现；</li>
 *   <li>找不到 / 出错只记日志，绝不崩游戏，指针本体照常能用。</li>
 * </ul>
 */
public final class AeronauticsCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger("coordinate_pointer/aeronautics");

    /** 航空学（Create Simulated）的模组 id。 */
    private static final String SIMULATED_MOD_ID = "simulated";
    /** 它的注册表和组件都在这个命名空间下。 */
    private static final String SIMULATED_NAMESPACE = "simulated";

    /** "导航目标"接口可能待过的位置，新版本换了包名时按顺序往下试。 */
    private static final String[] TARGET_INTERFACE_NAMES = {
            "dev.simulated_team.simulated.content.blocks.nav_table.navigation_target.NavigationTarget",
            "dev.simulated_team.simulated.content.blocks.nav_table.NavigationTarget",
            "dev.simulated_team.simulated.navigation_target.NavigationTarget",
            "dev.simulated_team.simulated.api.NavigationTarget",
            "dev.simulated_team.simulated.navigation.NavigationTarget",
    };

    private static final ResourceLocation OUR_TARGET_ID =
            ResourceLocation.fromNamespaceAndPath(CoordinatePointerMod.MOD_ID, "coordinate_pointer");

    /** 航空学的导航目标接口，没装航空学时是 null。 */
    private static Class<?> targetInterface;
    /** 我们塞进它注册表的那个对象（接口的代理）。 */
    private static Object target;

    /** 目标对象进没进航空学的注册表。 */
    private static boolean inRegistry;
    /** {@code simulated:target} 有没有挂到指针物品上——挂了导航台才肯收。 */
    private static boolean componentAttached;
    /** 只在第一次被问目标时打一条日志，方便以后排查。 */
    private static boolean loggedFirstCall;

    private AeronauticsCompat() {
    }

    /** 导航台联动生效了没（物品说明里也用它）。 */
    public static boolean isActive() {
        return componentAttached;
    }

    /** 在模组构造时调用一次。 */
    public static void bootstrap(IEventBus modBus) {
        if (!simulatedLoaded()) {
            return;
        }
        targetInterface = findTargetInterface();
        if (targetInterface != null) {
            LOGGER.info("[coordinate_pointer] 找到航空学的导航目标接口 {}", targetInterface.getName());
        }
        if (targetInterface == null) {
            LOGGER.warn("[coordinate_pointer] 找不到航空学的导航目标接口，导航台联动关闭（指针本体不受影响）");
            return;
        }
        try {
            target = Proxy.newProxyInstance(
                    targetInterface.getClassLoader(), new Class<?>[]{targetInterface}, AeronauticsCompat::handle);
        } catch (Throwable error) {
            LOGGER.warn("[coordinate_pointer] 无法创建导航目标对象，导航台联动关闭", error);
            return;
        }
        modBus.addListener(AeronauticsCompat::onRegister);
        modBus.addListener(AeronauticsCompat::onModifyDefaultComponents);
    }

    /** 装了航空学（Create Simulated）吗？查不到就当没装，绝不让这里把游戏卡崩。 */
    private static boolean simulatedLoaded() {
        try {
            return ModList.get() != null && ModList.get().isLoaded(SIMULATED_MOD_ID);
        } catch (Throwable notReady) {
            return false;
        }
    }

    /** 按候选名字找接口；找到后确认它确实像"能问出目标点"的接口。 */
    private static Class<?> findTargetInterface() {
        for (String name : TARGET_INTERFACE_NAMES) {
            Class<?> found = loadClass(name);
            if (found != null && looksLikeNavigationTarget(found)) {
                return found;
            }
        }
        return null;
    }

    /**
     * 按名字找类，两个类加载器都试一遍（模组自己的、以及当前线程的）。
     * 用 initialize=false 是故意的：只把类读进来看看长什么样，不触发它的静态初始化，
     * 免得在"模组刚加载"这个时间点上碰到别家还没准备好的东西。
     */
    private static Class<?> loadClass(String name) {
        try {
            return Class.forName(name, false, AeronauticsCompat.class.getClassLoader());
        } catch (Throwable first) {
            // 换当前线程的类加载器再试一次
        }
        try {
            ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
            return contextLoader == null ? null : Class.forName(name, false, contextLoader);
        } catch (Throwable second) {
            return null;
        }
    }

    /** 长这样的才认：是个接口，并且有方法能问出位置（Vec3 / BlockPos）。 */
    private static boolean looksLikeNavigationTarget(Class<?> candidate) {
        try {
            if (!candidate.isInterface()) {
                return false;
            }
            for (Method method : candidate.getMethods()) {
                Class<?> type = method.getReturnType();
                if (Vec3.class.isAssignableFrom(type) || BlockPos.class.isAssignableFrom(type)) {
                    return true;
                }
            }
        } catch (Throwable broken) {
            // 参数类型在别的模组里、这会儿还加载不出来，就当他不是。
        }
        return false;
    }

    /** 注册表里有没有我们那个对象（用原始类型比一下，不用管泛型）。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean containsTarget(Registry<?> registry) {
        return ((Registry) registry).getKey(target) != null;
    }

    /** 注册表路径认这些：{@code navigation_target}、{@code navigation_targets}、{@code nav_target}…。 */
    private static boolean isTargetRegistry(ResourceLocation key) {
        if (!SIMULATED_NAMESPACE.equals(key.getNamespace())) {
            return false;
        }
        return key.getPath().contains("target");
    }

    /** 把我们的目标对象注册进航空学的导航目标注册表。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void onRegister(RegisterEvent event) {
        if (target == null) {
            return;
        }
        ResourceKey<? extends Registry<?>> registryKey = event.getRegistryKey();
        if (!isTargetRegistry(registryKey.location())) {
            return;
        }
        try {
            event.register((ResourceKey) registryKey, OUR_TARGET_ID, () -> target);
            Registry<?> registry = event.getRegistry();
            inRegistry = registry != null && containsTarget(registry);
            if (inRegistry) {
                LOGGER.info("[coordinate_pointer] 导航目标已进航空学注册表 {}（条目 {}）",
                        registryKey.location(), OUR_TARGET_ID);
            } else {
                LOGGER.warn("[coordinate_pointer] 导航目标没能进航空学的注册表 {}（导航台可能不收指针）",
                        registryKey.location());
            }
        } catch (Throwable error) {
            // 有的版本会自己再包一层注册表，注册失败也继续往下走：
            // 只要组件挂上了，导航台一般还是能认。
            LOGGER.warn("[coordinate_pointer] 往 {} 注册导航目标失败", registryKey.location(), error);
        }
    }

    /**
     * 把 {@code simulated:target} 挂到坐标指针上。
     *
     * <p>注意：就算目标对象没进它的注册表，这个组件也照挂——组件值是物品的"默认值"，
     * 不会写进存档也不会网络同步，所以不会因为"注册表里没有"把物品弄坏；
     * 但挂不上组件，导航台就完全不肯收这个物品，那才是真的用不了。
     */
    private static void onModifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        DataComponentType<?> type = findTargetComponent();
        if (type == null) {
            LOGGER.warn("[coordinate_pointer] 没找到航空学的目标组件，导航台联动关闭");
            return;
        }
        try {
            event.modify(ModItems.COORDINATE_POINTER.get(), builder -> setComponent(builder, type));
            componentAttached = true;
            LOGGER.info("[coordinate_pointer] 已接入航空学导航台（注册表条目：{}）", inRegistry ? "已注册" : "未注册");
        } catch (Throwable error) {
            LOGGER.warn("[coordinate_pointer] 挂载导航目标组件失败，导航台联动关闭", error);
        }
    }

    /** 先找精确的 {@code simulated:target}，找不到就在同一个命名空间里找名字带 target 的组件。 */
    private static DataComponentType<?> findTargetComponent() {
        DataComponentType<?> exact = BuiltInRegistries.DATA_COMPONENT_TYPE.get(
                ResourceLocation.fromNamespaceAndPath(SIMULATED_NAMESPACE, "target"));
        if (exact != null) {
            return exact;
        }
        for (ResourceLocation id : BuiltInRegistries.DATA_COMPONENT_TYPE.keySet()) {
            if (SIMULATED_NAMESPACE.equals(id.getNamespace()) && id.getPath().contains("target")) {
                return BuiltInRegistries.DATA_COMPONENT_TYPE.get(id);
            }
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setComponent(DataComponentPatch.Builder builder, DataComponentType type) {
        builder.set(type, target);
    }

    // ------------------------------------------------------------------
    // 接口代理：不管方法叫什么名字，只要"问的是位置"就回答我们的坐标。
    // ------------------------------------------------------------------

    private static Object handle(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            return dispatch(proxy, method, args);
        } catch (Throwable error) {
            // 这里要是抛出去，导航台的 tick 就炸了，所以一律吃掉，最多记一笔。
            LOGGER.debug("[coordinate_pointer] 导航目标方法 {} 处理失败", method.getName(), error);
            return fallbackValue(method.getReturnType());
        }
    }

    /** 出错时给一个安全值：基本类型给 0/false，对象给 null。 */
    private static Object fallbackValue(Class<?> returnType) {
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == float.class) {
            return 0.0F;
        }
        if (returnType == double.class) {
            return 0.0D;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        return null;
    }

    private static Object dispatch(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        switch (name) {
            case "toString":
                return "coordinate_pointer:pointer";
            case "hashCode":
                return System.identityHashCode(proxy);
            case "equals":
                return args != null && args.length == 1 && proxy == args[0];
            default:
                break;
        }

        // 有默认实现的方法（红石强度、死区、最大距离那些）一律用航空学自己的算法，
        // 它会去问 NavTableBlockEntity.getTargetPosition()，而那个值正是我们回答的坐标。
        if (method.isDefault()) {
            return InvocationHandler.invokeDefault(proxy, method, args);
        }

        Class<?> returnType = method.getReturnType();
        if (Vec3.class.isAssignableFrom(returnType)) {
            return targetPosition(args);
        }
        if (returnType == Optional.class) {
            return Optional.ofNullable(targetPosition(args));
        }
        if (BlockPos.class.isAssignableFrom(returnType)) {
            Vec3 position = targetPosition(args);
            return position == null ? null : BlockPos.containing(position);
        }
        if (returnType == boolean.class || returnType == Boolean.class) {
            return targetPosition(args) != null;
        }
        if (returnType == float.class) {
            return 0.0F;
        }
        if (returnType == double.class) {
            return 0.0D;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        return null;
    }

    /** 从参数里认出导航台和物品栈（不管它俩排第几个），算出目标点。 */
    private static Vec3 targetPosition(Object[] args) {
        BlockEntity navTable = null;
        ItemStack stack = ItemStack.EMPTY;
        if (args != null) {
            for (Object arg : args) {
                if (navTable == null && arg instanceof BlockEntity blockEntity) {
                    navTable = blockEntity;
                } else if (arg instanceof ItemStack itemStack) {
                    stack = itemStack;
                }
            }
        }
        if (navTable == null || stack.isEmpty()) {
            return null;
        }
        Level level = navTable.getLevel();
        if (level == null) {
            return null;
        }
        Optional<TargetEntry> maybeTarget = PointerData.of(stack).activeTarget();
        if (maybeTarget.isEmpty()) {
            return null;
        }
        TargetEntry entry = maybeTarget.get();
        if (!level.dimension().location().equals(entry.dimension())) {
            // 不在同一个世界时也照样给方向：坐标是 X/Z，指向那边总比什么都不指有用，
            // 而且航空学的飞船（子世界）有时维度名对不上，这里不能因为这个就不指。
            LOGGER.debug("[coordinate_pointer] 指针记录的维度 {} 和导航台所在维度 {} 不同，仍按坐标给方向",
                    entry.dimension(), level.dimension().location());
        }
        // Y 用导航台自己的高度：导航台只关心水平方向，这样方向和红石信号才正常。
        double y = navTable.getBlockPos().getY() + 0.5D;
        Vec3 position = new Vec3(entry.x() + 0.5D, y, entry.z() + 0.5D);
        if (!loggedFirstCall) {
            loggedFirstCall = true;
            LOGGER.info("[coordinate_pointer] 导航台正在读取指针目标：X {} Z {}", entry.x(), entry.z());
        }
        return position;
    }
}
