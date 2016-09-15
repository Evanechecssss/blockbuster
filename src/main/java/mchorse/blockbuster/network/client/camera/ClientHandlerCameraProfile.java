package mchorse.blockbuster.network.client.camera;

import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.commands.CommandCamera;
import mchorse.blockbuster.network.client.ClientMessageHandler;
import mchorse.blockbuster.network.common.camera.PacketCameraProfile;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ClientHandlerCameraProfile extends ClientMessageHandler<PacketCameraProfile>
{
    @Override
    @SideOnly(Side.CLIENT)
    public void run(EntityPlayerSP player, PacketCameraProfile message)
    {
        CommandCamera.setProfile(message.profile);

        if (message.play)
        {
            ClientProxy.profileRunner.start();
        }

        player.addChatMessage(new TextComponentTranslation("blockbuster.profile.load", message.filename));
    }
}