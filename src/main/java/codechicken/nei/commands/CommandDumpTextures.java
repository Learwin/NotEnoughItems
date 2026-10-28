package codechicken.nei.commands;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import codechicken.core.CommonUtils;
import codechicken.nei.ItemList;
import cpw.mods.fml.common.registry.GameRegistry;

public class CommandDumpTextures extends CommandBase {

    private static void primeLighting() {
        Minecraft mc = Minecraft.getMinecraft();
        ItemStack dummy = new ItemStack(net.minecraft.init.Blocks.stone);

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();

        RenderHelper.enableGUIStandardItemLighting();
        RenderItem.getInstance().renderItemIntoGUI(mc.fontRenderer, mc.getTextureManager(), dummy, -100, -100);
        RenderHelper.disableStandardItemLighting();

        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    private static BufferedImage renderItemToImage(ItemStack stack, int size) {
        Minecraft mc = Minecraft.getMinecraft();

        Framebuffer fbo = new Framebuffer(size, size, true);
        fbo.bindFramebuffer(true);

        GL11.glViewport(0, 0, size, size);

        GL11.glClearColor(0f, 0f, 0f, 0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        RenderItem itemRenderer = RenderItem.getInstance();
        FontRenderer font = mc.fontRenderer;

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, size, size, 0.0D, 1000.0D, 3000.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);

        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.0F, 0.0F, -2000.0F);

        float scale = (float) size / 16f;
        GL11.glScalef(scale, scale, 1f);

        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_LIGHT0);
        GL11.glEnable(GL11.GL_COLOR_MATERIAL);
        RenderHelper.enableGUIStandardItemLighting();

        itemRenderer.renderItemIntoGUI(font, mc.getTextureManager(), stack, 0, 0);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);

        GL11.glPopMatrix();

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);

        BufferedImage img = captureScreenshot(0, 0, size, size);

        fbo.unbindFramebuffer();
        GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
        return img;
    }

    private static BufferedImage captureScreenshot(int x, int y, int width, int height) {
        int bpp = 4;
        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * bpp);
        GL11.glReadPixels(x, y, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                int index = (i + (width * j)) * bpp;
                int r = buffer.get(index) & 0xFF;
                int g = buffer.get(index + 1) & 0xFF;
                int b = buffer.get(index + 2) & 0xFF;
                int a = buffer.get(index + 3) & 0xFF;

                if (a == 0 && (r != 0 || g != 0 || b != 0)) {
                    a = 255;
                }

                image.setRGB(i, height - (j + 1), (a << 24) | (r << 16) | (g << 8) | b);
            }
        }

        return image;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public String getCommandName() {
        return "texturedump";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/texturedump <resolution(default 64)> to dump all item textures and block textures (3d)";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        int resolution = 64;
        if (args.length > 0) {
            resolution = Integer.valueOf(args[0]);
        }
        try {

            final File dir = new File(CommonUtils.getMinecraftDir(), "texturedump");
            if (!dir.exists()) {
                dir.mkdirs();
            }

            var blocks = ItemList.items.stream().filter(i -> i.getItem() instanceof ItemBlock)
                    .collect(Collectors.toList());
            var i = 0;
            var total = blocks.size();

            primeLighting();
            for (var block : blocks) {
                BufferedImage img = renderItemToImage(block, resolution);
                var resLoc = GameRegistry.findUniqueIdentifierFor(block.getItem());
                var fileName = resLoc.modId + "_" + resLoc.name + ".png";
                File file = new File(dir, fileName);
                ImageIO.write(img, "PNG", file);
                if (i % 1000 == 0) {
                    System.out.println("Saved: " + i + " from total: " + total);
                }
                i++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
