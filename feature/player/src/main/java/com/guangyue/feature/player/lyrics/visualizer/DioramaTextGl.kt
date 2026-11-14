// 镜台文字平面的 GL 层：对应 Folia DioramaScene.tsx 邻行 <mesh><meshBasicMaterial map transparent depthWrite=false/>
// 与 DioramaActiveUnitPlanes.tsx 的三层单元平面（辉光 / 灵魂加色、depthTest=false；字身普通混合、depthTest=false）。
// 着色器 = three MeshBasicMaterial 的子集：map（sRGB 纹理解码到线性）× color、opacity、线性雾（scene.fog = Fog(bg, 12, 30)），
// 最后编码回 sRGB（three outputColorSpace = SRGB）。混合在 sRGB 帧缓冲上做，与 three 一致。
package com.guangyue.feature.player.lyrics.visualizer

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal const val DIORAMA_TEXT_VERTEX_SHADER = """#version 300 es
layout(location = 0) in vec2 aPos;
uniform mat4 uModel;
uniform mat4 uView;
uniform mat4 uProj;
out vec2 vUv;
out float vFogDepth;
void main() {
    vUv = vec2(aPos.x + 0.5, 0.5 - aPos.y);
    vec4 mv = uView * uModel * vec4(aPos, 0.0, 1.0);
    vFogDepth = -mv.z;
    gl_Position = uProj * mv;
}
"""

internal const val DIORAMA_TEXT_FRAGMENT_SHADER = """#version 300 es
precision highp float;
in vec2 vUv;
in float vFogDepth;
uniform sampler2D uMap;
uniform vec3 uColor;
uniform float uOpacity;
uniform vec3 uFogColor;
uniform float uFogNear;
uniform float uFogFar;
out vec4 fragColor;
vec3 linearToSrgb(vec3 c) { return mix(c * 12.92, pow(c, vec3(0.41666)) * 1.055 - 0.055, vec3(greaterThan(c, vec3(0.0031308)))); }
void main() {
    // 栅格是 ALPHA_8 白字（Folia 画布 fillStyle 白、sRGB 解码后仍是 1）：颜色只来自 color，形状只来自 alpha。
    float a = texture(uMap, vUv).a;
    vec4 diffuse = vec4(uColor, uOpacity * a);
    float fogFactor = smoothstep(uFogNear, uFogFar, vFogDepth);
    diffuse.rgb = mix(diffuse.rgb, uFogColor, fogFactor);
    fragColor = vec4(linearToSrgb(diffuse.rgb), diffuse.a);
}
"""

/** 一张位图纹理（必须在 GL 线程创建 / 释放）。 */
internal class DioramaGlTexture(bitmap: Bitmap) {
    val id: Int
    init {
        val ids = IntArray(1); GLES30.glGenTextures(1, ids, 0); id = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }
    fun release() = GLES30.glDeleteTextures(1, intArrayOf(id), 0)
}

/** 平面的一次绘制：世界矩阵（列主序）、宽高（世界单位）、纹理、颜色（线性）、透明度、混合方式。 */
internal class DioramaTextDraw(
    val model: FloatArray,
    val width: Float,
    val height: Float,
    val texture: DioramaGlTexture,
    val color: DioramaLinearColor,
    val opacity: Float,
    val additive: Boolean,
    val depthTest: Boolean,
)

/** 位置 + 四元数 + 均匀缩放 → 列主序世界矩阵（three Matrix4.compose 同式）。 */
internal fun dioramaComposeMatrix(p: DioramaVec, q: DioramaQuat, s: Double, out: FloatArray = FloatArray(16)): FloatArray {
    val x = q.x; val y = q.y; val z = q.z; val w = q.w
    val x2 = x + x; val y2 = y + y; val z2 = z + z
    val xx = x * x2; val xy = x * y2; val xz = x * z2
    val yy = y * y2; val yz = y * z2; val zz = z * z2
    val wx = w * x2; val wy = w * y2; val wz = w * z2
    out[0] = ((1 - (yy + zz)) * s).toFloat(); out[1] = ((xy + wz) * s).toFloat(); out[2] = ((xz - wy) * s).toFloat(); out[3] = 0f
    out[4] = ((xy - wz) * s).toFloat(); out[5] = ((1 - (xx + zz)) * s).toFloat(); out[6] = ((yz + wx) * s).toFloat(); out[7] = 0f
    out[8] = ((xz + wy) * s).toFloat(); out[9] = ((yz - wx) * s).toFloat(); out[10] = ((1 - (xx + yy)) * s).toFloat(); out[11] = 0f
    out[12] = p.x.toFloat(); out[13] = p.y.toFloat(); out[14] = p.z.toFloat(); out[15] = 1f
    return out
}

/** 文字平面层：一个单位四边形 VAO，逐平面换矩阵 / 纹理 / 颜色。 */
internal class DioramaTextLayer {
    private var program = 0
    private var vao = 0
    private var vbo = 0
    private val model = FloatArray(16)
    private val local = FloatArray(16)

    fun init() {
        program = dioramaLinkProgram(DIORAMA_TEXT_VERTEX_SHADER, DIORAMA_TEXT_FRAGMENT_SHADER)
        val ids = IntArray(1)
        GLES30.glGenVertexArrays(1, ids, 0); vao = ids[0]
        GLES30.glGenBuffers(1, ids, 0); vbo = ids[0]
        val quad = floatArrayOf(-0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f)
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        val fb = ByteBuffer.allocateDirect(quad.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(quad); position(0) }
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, quad.size * 4, fb, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)
        GLES30.glBindVertexArray(0)
    }

    /** draws 按 three 的渲染顺序给（renderOrder 升序）；fogColor 线性。 */
    fun draw(draws: List<DioramaTextDraw>, view: FloatArray, projection: FloatArray, fogColor: DioramaLinearColor, fogNear: Float, fogFar: Float) {
        if (program == 0 || draws.isEmpty()) return
        GLES30.glUseProgram(program)
        fun loc(n: String) = GLES30.glGetUniformLocation(program, n)
        GLES30.glUniformMatrix4fv(loc("uView"), 1, false, view, 0)
        GLES30.glUniformMatrix4fv(loc("uProj"), 1, false, projection, 0)
        GLES30.glUniform3fv(loc("uFogColor"), 1, fogColor.toFloats(), 0)
        GLES30.glUniform1f(loc("uFogNear"), fogNear)
        GLES30.glUniform1f(loc("uFogFar"), fogFar)
        GLES30.glUniform1i(loc("uMap"), 0)
        val uModel = loc("uModel"); val uColor = loc("uColor"); val uOpacity = loc("uOpacity")
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glDepthMask(false)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindVertexArray(vao)
        for (d in draws) {
            if (d.opacity <= 0f) continue
            if (d.additive) GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
            else GLES30.glBlendFuncSeparate(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA, GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            if (d.depthTest) GLES30.glEnable(GLES30.GL_DEPTH_TEST) else GLES30.glDisable(GLES30.GL_DEPTH_TEST)
            Matrix.setIdentityM(local, 0)
            Matrix.scaleM(local, 0, d.width, d.height, 1f)
            Matrix.multiplyMM(model, 0, d.model, 0, local, 0)
            GLES30.glUniformMatrix4fv(uModel, 1, false, model, 0)
            GLES30.glUniform3fv(uColor, 1, d.color.toFloats(), 0)
            GLES30.glUniform1f(uOpacity, d.opacity)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, d.texture.id)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        }
        GLES30.glBindVertexArray(0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    fun release() {
        if (vao != 0) { GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0); GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0) }
        if (program != 0) GLES30.glDeleteProgram(program)
        vao = 0; vbo = 0; program = 0
    }
}
