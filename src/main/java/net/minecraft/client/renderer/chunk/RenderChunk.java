package net.minecraft.client.renderer.chunk;

import static net.lax1dude.eaglercraft.v1_8.opengl.RealOpenGLEnums.*;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import net.PeytonPlayz585.shadow.experimental.VisGraphExperimental;
import net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager;
import net.lax1dude.eaglercraft.v1_8.opengl.VertexFormat;
import net.lax1dude.eaglercraft.v1_8.opengl.WorldRenderer;
import net.lax1dude.eaglercraft.v1_8.opengl.ext.deferred.DeferredStateManager;
import net.lax1dude.eaglercraft.v1_8.opengl.ext.dynamiclights.DynamicLightsStateManager;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.RegionRenderCache;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumWorldBlockLayer;
import net.minecraft.world.World;

/**+
 * This portion of EaglercraftX contains deobfuscated Minecraft 1.8 source code.
 * 
 * Minecraft 1.8.8 bytecode is (c) 2015 Mojang AB. "Do not distribute!"
 * Mod Coder Pack v9.18 deobfuscation configs are (c) Copyright by the MCP Team
 * 
 * EaglercraftX 1.8 patch files (c) 2022-2024 lax1dude, ayunami2000. All Rights Reserved.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 */
public class RenderChunk {
	public static enum ShadowFrustumState {
		OUTSIDE, OUTSIDE_BB, INTERSECT, INSIDE
	}

	private World world;
	private final RenderGlobal renderGlobal;
	public static int renderChunksUpdated;
	private BlockPos position;
	public CompiledChunk compiledChunk = CompiledChunk.DUMMY;
	private ChunkCompileTaskGenerator compileTask = null;
	private final Set<TileEntity> field_181056_j = Sets.newHashSet();
	private final int index;
	private final float[] modelviewMatrix = new float[16];
	public AxisAlignedBB boundingBox;
	private int frameIndex = -1;
	private boolean needsUpdate = true;
	public int shadowLOD0FrameIndex = -1;
	public int shadowLOD1FrameIndex = -1;
	public int shadowLOD2FrameIndex = -1;
	public ShadowFrustumState shadowLOD0InFrustum = ShadowFrustumState.OUTSIDE;
	public ShadowFrustumState shadowLOD1InFrustum = ShadowFrustumState.OUTSIDE;
	public ShadowFrustumState shadowLOD2InFrustum = ShadowFrustumState.OUTSIDE;
	private BlockPos[] positionOffsets16 = new BlockPos[EnumFacing.VALUES.length];

	public RenderChunk(World worldIn, RenderGlobal renderGlobalIn, BlockPos blockPosIn, int indexIn) {
		this.world = worldIn;
		this.renderGlobal = renderGlobalIn;
		this.index = indexIn;
		if (!blockPosIn.equals(this.getPosition())) {
			this.setPosition(blockPosIn);
		}

	}

	public boolean setFrameIndex(int frameIndexIn) {
		if (this.frameIndex == frameIndexIn) {
			return false;
		} else {
			this.frameIndex = frameIndexIn;
			return true;
		}
	}

	public void setPosition(BlockPos pos) {
		this.stopCompileTask();
		this.position = pos;
		this.boundingBox = new AxisAlignedBB(pos, pos.add(16, 16, 16));

		this.initModelviewMatrix();
		
		for (int i = 0; i < this.positionOffsets16.length; ++i) {
            this.positionOffsets16[i] = null;
        }
	}

	public void resortTransparency(float x, float y, float z, ChunkCompileTaskGenerator generator) {
		CompiledChunk compiledchunk = generator.getCompiledChunk();
		if (compiledchunk.getState() != null && !compiledchunk.isLayerEmpty(EnumWorldBlockLayer.TRANSLUCENT)) {
			this.preRenderBlocks(
					generator.getRegionRenderCacheBuilder().getWorldRendererByLayer(EnumWorldBlockLayer.TRANSLUCENT),
					this.position);
			generator.getRegionRenderCacheBuilder().getWorldRendererByLayer(EnumWorldBlockLayer.TRANSLUCENT)
					.setVertexState(compiledchunk.getState());
			this.postRenderBlocks(EnumWorldBlockLayer.TRANSLUCENT, x, y, z,
					generator.getRegionRenderCacheBuilder().getWorldRendererByLayer(EnumWorldBlockLayer.TRANSLUCENT),
					compiledchunk);
		}
		if (DeferredStateManager.isRenderingRealisticWater() && compiledchunk.getStateRealisticWater() != null
				&& !compiledchunk.isLayerEmpty(EnumWorldBlockLayer.REALISTIC_WATER)) {
			this.preRenderBlocks(generator.getRegionRenderCacheBuilder()
					.getWorldRendererByLayer(EnumWorldBlockLayer.REALISTIC_WATER), this.position);
			generator.getRegionRenderCacheBuilder().getWorldRendererByLayer(EnumWorldBlockLayer.REALISTIC_WATER)
					.setVertexState(compiledchunk.getStateRealisticWater());
			this.postRenderBlocks(EnumWorldBlockLayer.REALISTIC_WATER, x, y, z, generator.getRegionRenderCacheBuilder()
					.getWorldRendererByLayer(EnumWorldBlockLayer.REALISTIC_WATER), compiledchunk);
		}
	}

	public void rebuildChunk(float x, float y, float z, ChunkCompileTaskGenerator generator) {
		CompiledChunk compiledchunk = new CompiledChunk();
		boolean flag = true;
		BlockPos blockpos = this.position;
		BlockPos blockpos1 = blockpos.add(15, 15, 15);

		RegionRenderCache regionrendercache;
		if (generator.getStatus() != ChunkCompileTaskGenerator.Status.COMPILING) {
			return;
		}

		regionrendercache = new RegionRenderCache(this.world, blockpos.add(-1, -1, -1), blockpos1.add(1, 1, 1), 1);
		generator.setCompiledChunk(compiledchunk);

		VisGraph visgraph = new VisGraph();
		VisGraphExperimental visgraphExperimental = new VisGraphExperimental();
		boolean b = Minecraft.getMinecraft().gameSettings.experimentalVisGraph;
		HashSet hashset = Sets.newHashSet();
		if (!regionrendercache.extendedLevelsInChunkCache()) {
			++renderChunksUpdated;
			boolean[] aboolean = new boolean[EnumWorldBlockLayer._VALUES.length];
			BlockRendererDispatcher blockrendererdispatcher = Minecraft.getMinecraft().getBlockRendererDispatcher();

			for (BlockPos blockpos$mutableblockpos : BlockPos.getAllInBox(blockpos, blockpos1)) {
				IBlockState iblockstate = regionrendercache.getBlockStateFaster(blockpos$mutableblockpos);
				Block block = iblockstate.getBlock();
				if (block.isOpaqueCube()) {
					if(!b) {
						visgraph.func_178606_a(blockpos$mutableblockpos);
					} else {
						visgraphExperimental.func_178606_a(blockpos$mutableblockpos);
					}
				}

				if (block.hasTileEntity()) {
					TileEntity tileentity = regionrendercache.getTileEntity(blockpos$mutableblockpos);
					TileEntitySpecialRenderer tileentityspecialrenderer = TileEntityRendererDispatcher.instance
							.getSpecialRenderer(tileentity);
					if (tileentity != null && tileentityspecialrenderer != null) {
						compiledchunk.addTileEntity(tileentity);
						if (tileentityspecialrenderer.func_181055_a()) {
							hashset.add(tileentity);
						}
					}
				}

				EnumWorldBlockLayer enumworldblocklayer1 = block.getBlockLayer();
				int i = enumworldblocklayer1.ordinal();
				if (block.getRenderType() != -1) {
					WorldRenderer worldrenderer = generator.getRegionRenderCacheBuilder().getWorldRendererByLayerId(i);
					if (!compiledchunk.isLayerStarted(enumworldblocklayer1)) {
						compiledchunk.setLayerStarted(enumworldblocklayer1);
						this.preRenderBlocks(worldrenderer, blockpos);
					}

					aboolean[i] |= blockrendererdispatcher.renderBlock(iblockstate, blockpos$mutableblockpos,
							regionrendercache, worldrenderer);

					if (block.eaglerShadersShouldRenderGlassHighlights()) {
						enumworldblocklayer1 = EnumWorldBlockLayer.GLASS_HIGHLIGHTS;
						worldrenderer = generator.getRegionRenderCacheBuilder()
								.getWorldRendererByLayerId(enumworldblocklayer1.ordinal());
						if (!compiledchunk.isLayerStarted(enumworldblocklayer1)) {
							compiledchunk.setLayerStarted(enumworldblocklayer1);
							this.preRenderBlocks(worldrenderer, blockpos);
						}

						aboolean[enumworldblocklayer1.ordinal()] |= blockrendererdispatcher.renderBlock(iblockstate,
								blockpos$mutableblockpos, regionrendercache, worldrenderer);
					}
				}
			}

			EnumWorldBlockLayer[] layers = EnumWorldBlockLayer._VALUES;
			for (int i = 0; i < layers.length; ++i) {
				EnumWorldBlockLayer enumworldblocklayer = layers[i];
				if (aboolean[enumworldblocklayer.ordinal()]) {
					compiledchunk.setLayerUsed(enumworldblocklayer);
				}

				if (compiledchunk.isLayerStarted(enumworldblocklayer)) {
					this.postRenderBlocks(enumworldblocklayer, x, y, z,
							generator.getRegionRenderCacheBuilder().getWorldRendererByLayer(enumworldblocklayer),
							compiledchunk);
				}
			}
		}

		if(!b) {
			compiledchunk.setVisibility(visgraph.computeVisibility());
		} else {
			compiledchunk.setVisibilityExperimental(visgraphExperimental.computeVisibility());
		}

		HashSet hashset1 = Sets.newHashSet(hashset);
		HashSet hashset2 = Sets.newHashSet(this.field_181056_j);
		hashset1.removeAll(this.field_181056_j);
		hashset2.removeAll(hashset);
		this.field_181056_j.clear();
		this.field_181056_j.addAll(hashset);
		this.renderGlobal.func_181023_a(hashset2, hashset1);

	}

	protected void finishCompileTask() {
		if (this.compileTask != null && this.compileTask.getStatus() != ChunkCompileTaskGenerator.Status.DONE) {
			this.compileTask.finish();
			this.compileTask = null;
		}
	}

	public ChunkCompileTaskGenerator makeCompileTaskChunk() {
		ChunkCompileTaskGenerator chunkcompiletaskgenerator;
		this.finishCompileTask();
		this.compileTask = new ChunkCompileTaskGenerator(this, ChunkCompileTaskGenerator.Type.REBUILD_CHUNK);
		chunkcompiletaskgenerator = this.compileTask;
		return chunkcompiletaskgenerator;
	}

	public ChunkCompileTaskGenerator makeCompileTaskTransparency() {
		this.compileTask = new ChunkCompileTaskGenerator(this, ChunkCompileTaskGenerator.Type.RESORT_TRANSPARENCY);
		this.compileTask.setCompiledChunk(this.compiledChunk);
		return this.compileTask;
	}

	private void preRenderBlocks(WorldRenderer worldRendererIn, BlockPos pos) {
		worldRendererIn.begin(7,
				(DeferredStateManager.isDeferredRenderer() || DynamicLightsStateManager.isDynamicLightsRender())
				? VertexFormat.BLOCK_SHADERS
				: DefaultVertexFormats.BLOCK);
		worldRendererIn.setTranslation((double) (-pos.getX()), (double) (-pos.getY()), (double) (-pos.getZ()));
	}

	private void postRenderBlocks(EnumWorldBlockLayer layer, float x, float y, float z, WorldRenderer worldRendererIn,
			CompiledChunk compiledChunkIn) {
		if ((layer == EnumWorldBlockLayer.TRANSLUCENT || layer == EnumWorldBlockLayer.REALISTIC_WATER)
				&& !compiledChunkIn.isLayerEmpty(layer)) {
			worldRendererIn.func_181674_a(x, y, z);
			if (layer == EnumWorldBlockLayer.REALISTIC_WATER) {
				compiledChunkIn.setStateRealisticWater(worldRendererIn.func_181672_a());
			} else {
				compiledChunkIn.setState(worldRendererIn.func_181672_a());
			}
		}

		worldRendererIn.finishDrawing();
	}

	private void initModelviewMatrix() {
		GlStateManager.pushMatrix();
		GlStateManager.loadIdentity();
		float f = 1.000001F;
		GlStateManager.translate(-8.0F, -8.0F, -8.0F);
		GlStateManager.scale(f, f, f);
		GlStateManager.translate(8.0F, 8.0F, 8.0F);
		GlStateManager.getFloat(GL_MODELVIEW_MATRIX, this.modelviewMatrix);
		GlStateManager.popMatrix();
	}

	public void multModelviewMatrix() {
		GlStateManager.multMatrix(this.modelviewMatrix);
	}

	public CompiledChunk getCompiledChunk() {
		return this.compiledChunk;
	}

	public void setCompiledChunk(CompiledChunk compiledChunkIn) {
		this.compiledChunk = compiledChunkIn;
	}

	public void stopCompileTask() {
		this.finishCompileTask();
		this.compiledChunk = CompiledChunk.DUMMY;
	}

	public void deleteGlResources() {
		this.stopCompileTask();
		this.world = null;
	}

	public BlockPos getPosition() {
		return this.position;
	}

	public void setNeedsUpdate(boolean needsUpdateIn) {
		this.needsUpdate = needsUpdateIn;
	}

	public boolean isNeedsUpdate() {
		return this.needsUpdate;
	}
	
	public BlockPos func_181701_a(EnumFacing p_getPositionOffset16_1_) {
        int i = p_getPositionOffset16_1_.getIndex();
        BlockPos blockpos = this.positionOffsets16[i];

        if (blockpos == null) {
            blockpos = this.getPosition().offset(p_getPositionOffset16_1_, 16);
            this.positionOffsets16[i] = blockpos;
        }

        return blockpos;
    }
}