package cn.blockforge.generated.coordinatepointer.client;

import cn.blockforge.generated.coordinatepointer.PointerLogic;
import cn.blockforge.generated.coordinatepointer.data.PointerData;
import cn.blockforge.generated.coordinatepointer.data.TargetEntry;
import cn.blockforge.generated.coordinatepointer.menu.CoordinatePointerMenu;
import cn.blockforge.generated.coordinatepointer.net.PointerNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 坐标录入界面：左边 5 个按钮是存档位（显示各自的名字），右边填名字、X、Z。
 *
 * <p>界面里所有编辑都先改一份本地副本（draft），停手半秒 / 换存档位 / 关掉界面时才发给服务器。
 * 这样既不用单独的"保存"按钮，也不会出现"切了格子还显示上一格的数字"。
 */
public class CoordinatePointerScreen extends Screen implements MenuAccess<CoordinatePointerMenu> {
    private static final int PANEL_BACKGROUND = 0xFF3A3A3A;
    private static final int PANEL_BORDER = 0xFF1C1C1C;
    private static final int PANEL_OUTER = 0xFF6E6E6E;
    private static final int TEXT_GRAY = 0xFFB0B0B0;
    private static final int SAVED_GREEN = 0xFF7CFC00;
    private static final int SLOT_BUTTON_WIDTH = 88;
    private static final int SLOT_BUTTON_HEIGHT = 20;
    private static final int SLOT_BUTTON_STEP = 22;
    private static final int RIGHT_COLUMN = 104;
    private static final int RIGHT_COLUMN_WIDTH = 88;
    /** 停手多少刻（20 刻 = 1 秒）后自动把编辑发给服务器。 */
    private static final int AUTOSAVE_TICKS = 8;
    /** "已保存"提示亮多久。 */
    private static final int SAVED_FLASH_TICKS = 40;

    private final CoordinatePointerMenu menu;
    private final Button[] slotButtons = new Button[PointerData.SLOT_COUNT];

    private EditBox nameBox;
    private EditBox xBox;
    private EditBox zBox;

    private PointerData draft = PointerData.EMPTY;
    private int active;
    private boolean loadedOnce;
    private int leftPos;
    private int topPos;
    /** 还有几刻就自动保存；0 = 没改动。 */
    private int pendingTicks;
    /** "已保存"这四个字还剩几刻。 */
    private int savedTicks;
    /** 正在用代码往输入框里填字：这时不要当成玩家编辑。 */
    private boolean loading;

    public CoordinatePointerScreen(CoordinatePointerMenu menu, Inventory inventory, Component title) {
        super(title);
        this.menu = menu;
    }

    @Override
    public CoordinatePointerMenu getMenu() {
        return this.menu;
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - CoordinatePointerMenu.IMAGE_WIDTH) / 2;
        this.topPos = (this.height - CoordinatePointerMenu.IMAGE_HEIGHT) / 2;

        if (!this.loadedOnce) {
            this.draft = this.currentData();
            this.active = this.draft.activeSlotSafe();
            this.loadedOnce = true;
        }

        for (int i = 0; i < PointerData.SLOT_COUNT; i++) {
            int index = i;
            Button button = Button.builder(Component.literal(String.valueOf(i + 1)), b -> this.selectSlot(index))
                    .bounds(this.leftPos + 8, this.topPos + 18 + i * SLOT_BUTTON_STEP,
                            SLOT_BUTTON_WIDTH, SLOT_BUTTON_HEIGHT)
                    .build();
            this.slotButtons[i] = button;
            this.addRenderableWidget(button);
        }

        this.nameBox = new EditBox(this.font, this.leftPos + RIGHT_COLUMN, this.topPos + 27, RIGHT_COLUMN_WIDTH, 18,
                Component.translatable("screen.coordinate_pointer.name_hint"));
        this.nameBox.setMaxLength(TargetEntry.MAX_NAME_LENGTH);
        this.nameBox.setHint(Component.translatable("screen.coordinate_pointer.name_hint"));
        this.nameBox.setResponder(text -> this.onFieldChanged());
        this.addRenderableWidget(this.nameBox);

        this.xBox = new EditBox(this.font, this.leftPos + RIGHT_COLUMN, this.topPos + 60, 42, 18,
                Component.translatable("screen.coordinate_pointer.x_hint"));
        this.configureNumberBox(this.xBox, "screen.coordinate_pointer.x_hint");

        this.zBox = new EditBox(this.font, this.leftPos + RIGHT_COLUMN + 46, this.topPos + 60, 42, 18,
                Component.translatable("screen.coordinate_pointer.z_hint"));
        this.configureNumberBox(this.zBox, "screen.coordinate_pointer.z_hint");

        // 没有"保存"按钮了：改完停手一会儿就会自动存，这个按钮只负责关掉界面。
        this.addRenderableWidget(Button.builder(Component.translatable("screen.coordinate_pointer.close"), b -> this.onClose())
                .bounds(this.leftPos + RIGHT_COLUMN, this.topPos + 100, RIGHT_COLUMN_WIDTH, 20)
                .build());

        this.loadFields();
        this.refreshButtons();
    }

    private void configureNumberBox(EditBox box, String hintKey) {
        box.setMaxLength(9);
        box.setHint(Component.translatable(hintKey));
        box.setFilter(text -> text.isEmpty() || text.matches("-?\\d{0,8}"));
        box.setResponder(text -> this.onFieldChanged());
        this.addRenderableWidget(box);
    }

    /** 输入框里的字刚变过：排一次自动保存，顺手刷新左边的存档位按钮。 */
    private void onFieldChanged() {
        if (!this.loading) {
            this.pendingTicks = AUTOSAVE_TICKS;
        }
        this.refreshButtons();
    }

    /** 把当前存档位的数据填进三个输入框。 */
    private void loadFields() {
        TargetEntry entry = this.draft.entry(this.active);
        this.loading = true;
        this.nameBox.setValue(entry.name());
        this.xBox.setValue(entry.set() ? Integer.toString(entry.x()) : "");
        this.zBox.setValue(entry.set() ? Integer.toString(entry.z()) : "");
        this.loading = false;
        this.pendingTicks = 0;
    }

    /** 把输入框里的内容整理成这一格该有的样子；两个都留空就是清空这一格（名字留着）。 */
    private TargetEntry buildEntry() {
        TargetEntry old = this.draft.entry(this.active);
        String rawX = this.xBox.getValue().trim();
        String rawZ = this.zBox.getValue().trim();
        String name = TargetEntry.cleanName(this.nameBox.getValue());
        if (rawX.isEmpty() && rawZ.isEmpty()) {
            return old.cleared().withName(name);
        }
        int x = rawX.isEmpty() ? 0 : this.clamp(this.parseOr(rawX, 0));
        int z = rawZ.isEmpty() ? 0 : this.clamp(this.parseOr(rawZ, 0));
        ResourceLocation dimension = old.set() ? old.dimension() : this.clientDimension();
        return new TargetEntry(true, x, z, name, dimension);
    }

    /** 把输入框里的内容写回本地副本。 */
    private void commitFields() {
        this.draft = this.draft.withEntry(this.active, this.buildEntry());
    }

    private boolean hasEdits() {
        return !this.buildEntry().equals(this.draft.entry(this.active));
    }

    private ResourceLocation clientDimension() {
        if (this.minecraft != null && this.minecraft.level != null) {
            return this.minecraft.level.dimension().location();
        }
        return TargetEntry.EMPTY.dimension();
    }

    private int clamp(int value) {
        return Mth.clamp(value, -PointerData.MAX_COORDINATE, PointerData.MAX_COORDINATE);
    }

    private int parseOr(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    /**
     * 读"这个界面绑定的那一把指针"自己的数据。
     *
     * <p>不能靠"手上拿着哪个"来猜：同时拿着两把指针、或者在主副手之间切换时，
     * 猜出来的可能是另一把，看起来就像两把指针的坐标共享了。
     * 界面在打开的时候就把物品格记在 menu 里了，这里只认那一格。
     */
    private PointerData currentData() {
        ItemStack stack = this.menu.pointerStack();
        if (stack.isEmpty()) {
            // 理论上不会走到这里；万一界面数据出问题，退回按手持找，至少还能用。
            Player player = this.minecraft == null ? null : this.minecraft.player;
            if (player == null) {
                return PointerData.EMPTY;
            }
            stack = PointerLogic.findHeldPointer(player);
        }
        return stack.isEmpty() ? PointerData.EMPTY : PointerData.of(stack);
    }

    /** 点存档位：先记住刚编辑的内容，再切到这一格并把它自己的数据填进来。 */
    private void selectSlot(int index) {
        if (index == this.active) {
            return;
        }
        this.flushEdits();
        this.commitFields();
        this.draft = this.draft.withActiveSlot(index);
        this.active = index;
        this.loadFields();
        this.refreshButtons();
        this.send(false);
    }

    /** 把输入框的内容存进本地副本并发给服务器；真的改了才发，没改就什么都不做。 */
    private void flushEdits() {
        this.pendingTicks = 0;
        if (!this.hasEdits()) {
            return;
        }
        this.commitFields();
        this.send(false);
        this.savedTicks = SAVED_FLASH_TICKS;
        this.refreshButtons();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.savedTicks > 0) {
            this.savedTicks--;
        }
        if (this.pendingTicks > 0 && --this.pendingTicks == 0) {
            this.flushEdits();
        }
    }

    private void send(boolean announce) {
        // 连"是哪一格物品"一起发：服务器只会把数据写回这一把指针。
        PacketDistributor.sendToServer(
                new PointerNetwork.UpdatePointerPayload(this.draft, this.menu.pointerSlot(), announce));
    }

    private void refreshButtons() {
        for (int i = 0; i < this.slotButtons.length; i++) {
            // 正在编辑的这一格用输入框里的名字，其它格用记事本里的。
            TargetEntry entry = i == this.active ? this.buildEntry() : this.draft.entry(i);
            String marker = entry.set() ? "●" : "○";
            // 没起名字的格子就叫 A / B / C …，物品名后面的后缀也是这个名字。
            // 字母按格子配色（红蓝黄绿紫），和物品名后面那截保持一致。
            String name = PointerLogic.slotColor(i) + PointerLogic.slotLabel(entry, i);
            String color = i == this.active ? "§e" : (entry.set() ? "§f" : "§7");
            String label = this.font.plainSubstrByWidth(color + marker + " " + name, SLOT_BUTTON_WIDTH - 8);
            this.slotButtons[i].setMessage(Component.literal(label));
        }
    }

    @Override
    public void onClose() {
        // 关界面时把还没发出去的编辑补发掉，不会出现"打了字没存上"。
        this.flushEdits();
        super.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 先把整屏幕压暗一层（只是变暗，不做模糊），再画面板，最后 super.render 画按钮和输入框。
        graphics.fill(0, 0, this.width, this.height, 0x99000000);

        int left = this.leftPos;
        int top = this.topPos;
        int right = left + CoordinatePointerMenu.IMAGE_WIDTH;
        int bottom = top + CoordinatePointerMenu.IMAGE_HEIGHT;

        graphics.fill(left - 2, top - 2, right + 2, bottom + 2, PANEL_OUTER);
        graphics.fill(left - 1, top - 1, right + 1, bottom + 1, PANEL_BORDER);
        graphics.fill(left, top, right, bottom, PANEL_BACKGROUND);

        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, left + CoordinatePointerMenu.IMAGE_WIDTH / 2, top + 6, 0xFFFFFF);

        int x0 = left + RIGHT_COLUMN;
        graphics.drawString(this.font, Component.translatable("screen.coordinate_pointer.name"), x0, top + 17, TEXT_GRAY, false);
        graphics.drawString(this.font, Component.literal("X"), x0, top + 50, TEXT_GRAY, false);
        graphics.drawString(this.font, Component.literal("Z"), x0 + 46, top + 50, TEXT_GRAY, false);

        TargetEntry preview = this.buildEntry();
        Component info = preview.set()
                ? Component.translatable("screen.coordinate_pointer.info", preview.x(), preview.z())
                : Component.translatable("screen.coordinate_pointer.info_empty");
        graphics.drawString(this.font, info, x0, top + 84, 0xFFFFFF, false);

        // 在界面里直接给个"存好了"的反馈，不用靠屏幕外面的提示。
        if (this.savedTicks > 0) {
            graphics.drawString(this.font, Component.translatable("screen.coordinate_pointer.saved"),
                    x0, top + 122, SAVED_GREEN, false);
        } else if (this.pendingTicks > 0) {
            graphics.drawString(this.font, Component.translatable("screen.coordinate_pointer.saving"),
                    x0, top + 122, TEXT_GRAY, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * 背景只压暗，不加模糊。
     *
     * <p>父类会给整个屏幕来一层"模糊 + 菜单纹理"的背景，开着面板时中间糊成一片、看不清字，
     * 所以这里整个盖掉：要的那层压暗在 {@link #render} 里自己画。
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }
}
