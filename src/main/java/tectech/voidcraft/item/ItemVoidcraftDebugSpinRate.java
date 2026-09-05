package tectech.voidcraft.item;

import static net.minecraft.util.StatCollector.translateToLocal;
import static net.minecraft.util.StatCollector.translateToLocalFormatted;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import com.gtnewhorizon.gtnhlib.item.ItemStackNBT;

import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import tectech.TecTech;
import tectech.voidcraft.uss.MTEUnstableSolarSystem;

/**
 * Debug item: right-click an UnstableSolarSystem machine to link this tool to it and cycle its star's spin-rate
 * multiplier through a preset list plus a continuous sweep. Right-click in open air afterward to cycle it again
 * remotely.
 */
public final class ItemVoidcraftDebugSpinRate extends Item {

    private static final String LINK_DIM_TAG = "linkDim";
    private static final String LINK_X_TAG = "linkX";
    private static final String LINK_Y_TAG = "linkY";
    private static final String LINK_Z_TAG = "linkZ";

    /** The dedicated 16×16 item icon. */
    public static final String ICON_NAME = "tectech:iconsets/VC_ITEM_DEBUG_SPIN_RATE";

    public static ItemVoidcraftDebugSpinRate INSTANCE;

    private static IIcon icon;

    private ItemVoidcraftDebugSpinRate() {
        setMaxStackSize(1);
        setUnlocalizedName("tt.voidcraft_debug_spin_rate");
        setCreativeTab(TecTech.creativeTabTecTech);
    }

    /** Records the machine's position as this stack's link target, replacing any previous link. */
    public static void link(ItemStack stack, World world, int x, int y, int z) {
        NBTTagCompound tag = ItemStackNBT.get(stack);
        tag.setInteger(LINK_DIM_TAG, world.provider.dimensionId);
        tag.setInteger(LINK_X_TAG, x);
        tag.setInteger(LINK_Y_TAG, y);
        tag.setInteger(LINK_Z_TAG, z);
    }

    /**
     * @return the linked machine (null when this stack has never been linked, its world isn't loaded, or
     *         whatever is there is no longer an UnstableSolarSystem machine)
     */
    private static MTEUnstableSolarSystem linkedMachine(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(LINK_DIM_TAG)) {
            return null;
        }
        World world = DimensionManager.getWorld(tag.getInteger(LINK_DIM_TAG));
        if (world == null) {
            return null;
        }
        TileEntity te = world
            .getTileEntity(tag.getInteger(LINK_X_TAG), tag.getInteger(LINK_Y_TAG), tag.getInteger(LINK_Z_TAG));
        if (!(te instanceof IGregTechTileEntity igte)) {
            return null;
        }
        return igte.getMetaTileEntity() instanceof MTEUnstableSolarSystem uss ? uss : null;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        // Open-air click only; a click on a machine is handled by the on-block debug effect instead.
        if (world.isRemote) {
            return stack;
        }
        MTEUnstableSolarSystem machine = linkedMachine(stack);
        if (machine == null) {
            player.addChatMessage(
                new ChatComponentText(translateToLocal("tt.voidcraft_debug_spin_rate.not_linked")));
            return stack;
        }
        String label = machine.debugCycleStarSpinRate();
        if (label != null) {
            player.addChatMessage(
                new ChatComponentText(
                    translateToLocalFormatted("tt.voidcraft_debug_spin_rate.feedback", label)));
        } else {
            player.addChatMessage(
                new ChatComponentText(translateToLocal("tt.voidcraft_debug_spin_rate.no_star")));
        }
        return stack;
    }

    @Override
    public void addInformation(ItemStack aStack, EntityPlayer ep, List<String> aList, boolean boo) {
        aList.add(EnumChatFormatting.GRAY + translateToLocal("tt.voidcraft_debug_spin_rate.hint"));
        NBTTagCompound tag = aStack.getTagCompound();
        if (tag != null && tag.hasKey(LINK_DIM_TAG)) {
            aList.add(
                EnumChatFormatting.AQUA + translateToLocalFormatted(
                    "tt.voidcraft_debug_spin_rate.linked",
                    tag.getInteger(LINK_X_TAG),
                    tag.getInteger(LINK_Y_TAG),
                    tag.getInteger(LINK_Z_TAG)));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister iconRegister) {
        icon = iconRegister.registerIcon(ICON_NAME);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(ItemStack aStack, int pass) {
        return icon != null ? icon : super.getIcon(aStack, pass);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int damage) {
        return icon != null ? icon : super.getIconFromDamage(damage);
    }

    public static void run() {
        INSTANCE = new ItemVoidcraftDebugSpinRate();
        GameRegistry.registerItem(INSTANCE, INSTANCE.getUnlocalizedName());
    }
}
