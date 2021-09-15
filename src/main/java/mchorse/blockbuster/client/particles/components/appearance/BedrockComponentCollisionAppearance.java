package mchorse.blockbuster.client.particles.components.appearance;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.netty.handler.codec.json.JsonObjectDecoder;
import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.client.particles.BedrockMaterial;
import mchorse.blockbuster.client.particles.BedrockScheme;
import mchorse.blockbuster.client.particles.components.BedrockComponentBase;
import mchorse.blockbuster.client.particles.components.IComponentParticleRender;
import mchorse.blockbuster.client.particles.emitter.BedrockEmitter;
import mchorse.blockbuster.client.particles.emitter.BedrockParticle;
import mchorse.blockbuster.client.particles.molang.MolangException;
import mchorse.blockbuster.client.particles.molang.MolangParser;
import mchorse.blockbuster.client.particles.molang.expressions.MolangExpression;
import mchorse.blockbuster.client.textures.GifTexture;
import mchorse.mclib.utils.Interpolations;
import mchorse.mclib.utils.resources.RLUtils;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.vecmath.Matrix4f;
import javax.vecmath.Vector3d;
import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;

public class BedrockComponentCollisionAppearance extends BedrockComponentAppearanceBillboard implements IComponentParticleRender
{
    /* Options */
    public BedrockMaterial material = BedrockMaterial.OPAQUE;
    public ResourceLocation texture = BedrockScheme.DEFAULT_TEXTURE;
    
    public MolangExpression enabled = MolangParser.ZERO;

    public boolean lit; //gets set from GuiCollisionLighting

    @Override
    public BedrockComponentBase fromJson(JsonElement elem, MolangParser parser) throws MolangException
    {
        if (!elem.isJsonObject()) return super.fromJson(elem, parser);

        JsonObject element = elem.getAsJsonObject();

        if (element.has("enabled")) this.enabled = parser.parseJson(element.get("enabled"));
        if (element.has("lit"))
        {
            this.lit = element.get("lit").getAsBoolean();
        }
        
        if (element.has("material"))
        {
            this.material = BedrockMaterial.fromString(element.get("material").getAsString());
        }
        
        if (element.has("texture"))
        {
            String texture = element.get("texture").getAsString();

            if (!texture.equals("textures/particle/particles"))
            {
                this.texture = RLUtils.create(texture);
            }
        }

        return super.fromJson(element, parser);
    }

    @Override
    public JsonElement toJson()
    {
        JsonObject object = new JsonObject();
        
        object.add("enabled", this.enabled.toJson());
        object.addProperty("lit", this.lit);
        object.addProperty("material", this.material.id);
        
        if (this.texture != null && !this.texture.equals(BedrockScheme.DEFAULT_TEXTURE))
        {
            object.addProperty("texture", this.texture.toString());
        }

        /* add the default stuff from super */
        JsonObject superJson = (JsonObject) super.toJson();
        Set<Map.Entry<String, JsonElement>> entries = superJson.entrySet();

        for(Map.Entry<String, JsonElement> entry : entries)
        {
            object.add(entry.getKey(), entry.getValue());
        }

        return object;
    }

    @Override
    public void preRender(BedrockEmitter emitter, float partialTicks)
    {}

    @Override
    public void render(BedrockEmitter emitter, BedrockParticle particle, BufferBuilder builder, float partialTicks)
    {
        boolean tmpLit = false;

        if (!particle.isCollisionTexture(emitter))
        {
            if (particle.isCollisionTinting(emitter))
            {
                tmpLit = emitter.lit;
                emitter.lit = this.lit;
                emitter.scheme.get(BedrockComponentAppearanceBillboard.class).render(emitter, particle, builder, partialTicks);
                emitter.lit = tmpLit;
            }

            return; //when texture and tinting is false - this render method should not be used
        }
        else if (!particle.isCollisionTinting(emitter))
        {
            //tinting false doesn't necessarily mean that lit was not passed - emitter.lit should be used
            tmpLit = this.lit;
            this.lit = emitter.lit;
        }
        
        this.calculateUVs(particle, partialTicks);

        /* Render the particle */
        double px = Interpolations.lerp(particle.prevPosition.x, particle.position.x, partialTicks);
        double py = Interpolations.lerp(particle.prevPosition.y, particle.position.y, partialTicks);
        double pz = Interpolations.lerp(particle.prevPosition.z, particle.position.z, partialTicks);
        float angle = Interpolations.lerp(particle.prevRotation, particle.rotation, partialTicks);

        Vector3d pos = this.calculatePosition(emitter, particle, px, py, pz);
        px = pos.x;
        py = pos.y;
        pz = pos.z;

        /* Calculate yaw and pitch based on the facing mode */
        float entityYaw = emitter.cYaw;
        float entityPitch = emitter.cPitch;
        double entityX = emitter.cX;
        double entityY = emitter.cY;
        double entityZ = emitter.cZ;
        boolean lookAt = this.facing == CameraFacing.LOOKAT_XYZ || this.facing == CameraFacing.LOOKAT_Y;

        /* Flip width when frontal perspective mode */
        if (emitter.perspective == 2)
        {
            this.w = -this.w;
        }
        /* In GUI renderer */
        else if (emitter.perspective == 100 && !lookAt)
        {
            entityYaw = 180 - entityYaw;

            this.w = -this.w;
            this.h = -this.h;
        }

        if (lookAt)
        {
            double dX = entityX - px;
            double dY = entityY - py;
            double dZ = entityZ - pz;
            double horizontalDistance = MathHelper.sqrt(dX * dX + dZ * dZ);

            entityYaw = 180 - (float) (MathHelper.atan2(dZ, dX) * (180D / Math.PI)) - 90.0F;
            entityPitch = (float) (-(MathHelper.atan2(dY, horizontalDistance) * (180D / Math.PI))) + 180;
        }

        /* Calculate the geometry for billboards using cool matrix math */
        int light = this.lit ? 15728880 : emitter.getBrightnessForRender(partialTicks, px, py, pz);
        int lightX = light >> 16 & 65535;
        int lightY = light & 65535;

        this.calculateVertices(emitter, particle);

        if (this.facing == CameraFacing.ROTATE_XYZ || this.facing == CameraFacing.LOOKAT_XYZ)
        {
            this.rotation.rotY(entityYaw / 180 * (float) Math.PI);
            this.transform.mul(this.rotation);
            this.rotation.rotX(entityPitch / 180 * (float) Math.PI);
            this.transform.mul(this.rotation);
        }
        else if (this.facing == CameraFacing.ROTATE_Y || this.facing == CameraFacing.LOOKAT_Y) {
            this.rotation.rotY(entityYaw / 180 * (float) Math.PI);
            this.transform.mul(this.rotation);
        }

        this.rotation.rotZ(angle / 180 * (float) Math.PI);
        this.transform.mul(this.rotation);
        this.transform.setTranslation(new Vector3f((float) px, (float) py, (float) pz));

        for (Vector4f vertex : this.vertices)
        {
            this.transform.transform(vertex);
        }

        float u1 = this.u1 / (float) this.textureWidth;
        float u2 = this.u2 / (float) this.textureWidth;
        float v1 = this.v1 / (float) this.textureHeight;
        float v2 = this.v2 / (float) this.textureHeight;

        builder.pos(this.vertices[0].x, this.vertices[0].y, this.vertices[0].z).tex(u1, v1).lightmap(lightX, lightY).color(particle.r, particle.g, particle.b, particle.a).endVertex();
        builder.pos(this.vertices[1].x, this.vertices[1].y, this.vertices[1].z).tex(u2, v1).lightmap(lightX, lightY).color(particle.r, particle.g, particle.b, particle.a).endVertex();
        builder.pos(this.vertices[2].x, this.vertices[2].y, this.vertices[2].z).tex(u2, v2).lightmap(lightX, lightY).color(particle.r, particle.g, particle.b, particle.a).endVertex();
        builder.pos(this.vertices[3].x, this.vertices[3].y, this.vertices[3].z).tex(u1, v2).lightmap(lightX, lightY).color(particle.r, particle.g, particle.b, particle.a).endVertex();

        if (!particle.isCollisionTinting(emitter))
        {
            this.lit = tmpLit;
        }
    }

    @Override //not really important because it seems to be used for guiParticles - there is no collision
    public void renderOnScreen(BedrockParticle particle, int x, int y, float scale, float partialTicks)
    { }

    @Override
    public void calculateUVs(BedrockParticle particle, float partialTicks)
    {
        /* Update particle's UVs and size */
        this.w = (float) this.sizeW.get() * 2.25F;
        this.h = (float) this.sizeH.get() * 2.25F;

        float u = (float) this.uvX.get();
        float v = (float) this.uvY.get();
        float w = (float) this.uvW.get();
        float h = (float) this.uvH.get();

        if (this.flipbook)
        {
            int index = (int) (particle.getAge(partialTicks) * this.fps);
            int max = (int) this.maxFrame.get();

            if (this.stretchFPS)
            {
                float lifetime = (particle.lifetime <= 0) ? 0 : (particle.age + partialTicks) / (particle.lifetime-particle.firstCollision);
                //for collided particles with expiration - stretch differently since lifetime changed
                //note to myself - it could be that the molang expression of the delay expiration is updated every tick and therefore changes constantly for one particle - this is bad
                if (particle.expireAge!=0) lifetime = (particle.lifetime <= 0) ? 0 : (particle.age + partialTicks) / (particle.expirationDelay);
                index = (int) (lifetime * max);
            }

            if (this.loop && max != 0)
            {
                index = index % max;
            }

            if (index > max)
            {
                index = max;
            }

            u += this.stepX * index;
            v += this.stepY * index;
        }

        this.u1 = u;
        this.v1 = v;
        this.u2 = u + w;
        this.v2 = v + h;
    }

    @Override
    public void postRender(BedrockEmitter emitter, float partialTicks)
    {}
    
    @Override
    public int getSortingIndex()
    {
        return 200;
    }
}