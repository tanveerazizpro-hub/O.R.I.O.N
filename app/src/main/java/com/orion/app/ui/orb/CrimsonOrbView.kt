package com.orion.app.ui.orb

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

/**
 * Cinematic 3D orb, GPU-rendered with OpenGL ES 2.0.
 *
 * Visual layers (all inside the fragment shader):
 *  - Fresnel rim glow
 *  - Inner hot core falloff
 *  - Two-noise shimmer that ripples across the surface
 *  - Time-based breathing pulse
 *  - Subtle vertical energy bands
 *
 * Low-end optimization:
 *  - GLES 2.0 only (works on ~any Android device from 2013 onward)
 *  - 30fps frame cap
 *  - 32x20 sphere (~1200 triangles)
 *  - Single shader, no textures, no external libraries
 */
class CrimsonOrbView(context: Context) : GLSurfaceView(context) {

    private val orbRenderer = CinematicOrbRenderer()

    init {
        setEGLContextClientVersion(2)
        setRenderer(orbRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    /** Set the accent (r, g, b in 0..1). Safe to call from UI thread. */
    fun setAccent(r: Float, g: Float, b: Float) {
        orbRenderer.accentR = r
        orbRenderer.accentG = g
        orbRenderer.accentB = b
    }

    /** 0..1 — drives the "alive" feeling (louder = brighter). */
    fun setEnergy(e: Float) {
        orbRenderer.energy = e.coerceIn(0f, 1f)
    }
}

private class CinematicOrbRenderer : GLSurfaceView.Renderer {

    @Volatile var accentR: Float = 0.42f
    @Volatile var accentG: Float = 0.55f
    @Volatile var accentB: Float = 1.00f
    @Volatile var energy: Float = 0.5f

    private var program = 0
    private var aPos = 0
    private var aNormal = 0
    private var uMvp = 0
    private var uModel = 0
    private var uTime = 0
    private var uAccent = 0
    private var uEnergy = 0

    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var normalBuffer: FloatBuffer
    private lateinit var indexBuffer: ShortBuffer
    private var indexCount = 0

    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private var startNanos = 0L
    private var lastFrameNanos = 0L
    private val minFrameInterval = 33_333_333L  // 30 fps

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.039f, 0.055f, 0.101f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        // Additive-ish blending for a soft "energy" feel
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        aNormal = GLES20.glGetAttribLocation(program, "aNormal")
        uMvp = GLES20.glGetUniformLocation(program, "uMvp")
        uModel = GLES20.glGetUniformLocation(program, "uModel")
        uTime = GLES20.glGetUniformLocation(program, "uTime")
        uAccent = GLES20.glGetUniformLocation(program, "uAccent")
        uEnergy = GLES20.glGetUniformLocation(program, "uEnergy")

        buildSphere(segments = 40, rings = 24)

        startNanos = System.nanoTime()
        lastFrameNanos = startNanos
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(projMatrix, 0, 45f, aspect, 0.1f, 100f)
        Matrix.setLookAtM(
            viewMatrix, 0,
            0f, 0f, 3.4f,
            0f, 0f, 0f,
            0f, 1f, 0f
        )
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        if (now - lastFrameNanos < minFrameInterval) return
        lastFrameNanos = now

        val t = (now - startNanos) / 1_000_000_000f

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.rotateM(modelMatrix, 0, t * 14f, 0f, 1f, 0f)
        Matrix.rotateM(modelMatrix, 0, sin(t * 0.5f) * 12f, 1f, 0f, 0f)

        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, mvpMatrix, 0)

        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uModel, 1, false, modelMatrix, 0)
        GLES20.glUniform1f(uTime, t)
        GLES20.glUniform3f(uAccent, accentR, accentG, accentB)
        GLES20.glUniform1f(uEnergy, energy)

        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(aPos)

        normalBuffer.position(0)
        GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, 0, normalBuffer)
        GLES20.glEnableVertexAttribArray(aNormal)

        indexBuffer.position(0)
        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            indexCount,
            GLES20.GL_UNSIGNED_SHORT,
            indexBuffer
        )

        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aNormal)
    }

    private fun buildSphere(segments: Int, rings: Int) {
        val vertexCount = (segments + 1) * (rings + 1)
        val vertices = FloatArray(vertexCount * 3)
        val normals = FloatArray(vertexCount * 3)

        var vi = 0
        for (y in 0..rings) {
            val v = y.toFloat() / rings
            val phi = v * Math.PI.toFloat()
            for (x in 0..segments) {
                val u = x.toFloat() / segments
                val theta = u * (2f * Math.PI).toFloat()
                val nx = sin(phi) * cos(theta)
                val ny = cos(phi)
                val nz = sin(phi) * sin(theta)
                vertices[vi] = nx
                vertices[vi + 1] = ny
                vertices[vi + 2] = nz
                normals[vi] = nx
                normals[vi + 1] = ny
                normals[vi + 2] = nz
                vi += 3
            }
        }

        val indices = ShortArray(segments * rings * 6)
        var ii = 0
        for (y in 0 until rings) {
            for (x in 0 until segments) {
                val a = y * (segments + 1) + x
                val b = a + 1
                val c = a + (segments + 1)
                val d = c + 1
                indices[ii++] = a.toShort()
                indices[ii++] = c.toShort()
                indices[ii++] = b.toShort()
                indices[ii++] = b.toShort()
                indices[ii++] = c.toShort()
                indices[ii++] = d.toShort()
            }
        }

        indexCount = indices.size

        vertexBuffer = ByteBuffer
            .allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(vertices); position(0) }

        normalBuffer = ByteBuffer
            .allocateDirect(normals.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(normals); position(0) }

        indexBuffer = ByteBuffer
            .allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply { put(indices); position(0) }
    }

    private fun buildProgram(vs: String, fs: String): Int {
        val v = compileShader(GLES20.GL_VERTEX_SHADER, vs)
        val f = compileShader(GLES20.GL_FRAGMENT_SHADER, fs)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v)
        GLES20.glAttachShader(p, f)
        GLES20.glLinkProgram(p)
        return p
    }

    private fun compileShader(type: Int, source: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, source)
        GLES20.glCompileShader(s)
        return s
    }

    companion object {

        private const val VERTEX_SHADER = """
            uniform mat4 uMvp;
            uniform mat4 uModel;
            attribute vec3 aPos;
            attribute vec3 aNormal;
            varying vec3 vNormal;
            varying vec3 vWorldPos;
            void main() {
                vNormal = mat3(uModel) * aNormal;
                vWorldPos = (uModel * vec4(aPos, 1.0)).xyz;
                gl_Position = uMvp * vec4(aPos, 1.0);
            }
        """

        /**
         * Cinematic orb fragment shader.
         *
         * Layers:
         *  1. Fresnel rim (bright edge when viewed at grazing angle)
         *  2. Core brightness (hot center)
         *  3. Two tone-mapped noise passes → energy ripples
         *  4. Vertical energy bands
         *  5. Overall breathing pulse (driven by uTime + uEnergy)
         */
        private const val FRAGMENT_SHADER = """
            precision highp float;

            uniform vec3 uAccent;
            uniform float uTime;
            uniform float uEnergy;
            varying vec3 vNormal;
            varying vec3 vWorldPos;

            // --- Cheap hash/noise functions (no textures) ---
            float hash(vec3 p) {
                p = fract(p * 0.3183099 + vec3(0.1, 0.2, 0.3));
                p *= 17.0;
                return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
            }
            float noise(vec3 x) {
                vec3 i = floor(x);
                vec3 f = fract(x);
                f = f * f * (3.0 - 2.0 * f);
                return mix(
                    mix(mix(hash(i + vec3(0,0,0)), hash(i + vec3(1,0,0)), f.x),
                        mix(hash(i + vec3(0,1,0)), hash(i + vec3(1,1,0)), f.x), f.y),
                    mix(mix(hash(i + vec3(0,0,1)), hash(i + vec3(1,0,1)), f.x),
                        mix(hash(i + vec3(0,1,1)), hash(i + vec3(1,1,1)), f.x), f.y),
                    f.z
                );
            }

            void main() {
                vec3 N = normalize(vNormal);
                vec3 V = normalize(-vWorldPos);

                // Facing factor: 1 = facing camera, 0 = edge
                float facing = max(dot(N, V), 0.0);

                // Fresnel rim — bright at the edge
                float rim = pow(1.0 - facing, 2.4);

                // Hot core — bright at the center facing camera
                float core = pow(facing, 1.6);

                // Two noise passes for organic ripples
                vec3 nCoord1 = N * 3.5 + vec3(uTime * 0.35);
                vec3 nCoord2 = N * 6.5 - vec3(uTime * 0.55, uTime * 0.40, 0.0);
                float n1 = noise(nCoord1);
                float n2 = noise(nCoord2);
                float ripple = (n1 * 0.6 + n2 * 0.4) * (0.35 + uEnergy * 0.65);

                // Vertical energy bands (like the FF ribbons)
                float bands = 0.5 + 0.5 * sin(N.y * 22.0 - uTime * 2.2);
                bands = pow(bands, 3.0) * 0.5;

                // Breathing
                float breath = 0.88 + 0.12 * sin(uTime * 2.0);

                // Compose colors
                vec3 coreCol = vec3(1.0, 0.98, 0.94);
                vec3 midCol  = uAccent;
                vec3 rimCol  = uAccent * 1.6;

                // Base body
                vec3 col = mix(midCol, coreCol, core);
                col = mix(col, rimCol, rim);

                // Add ripples (accent-tinted)
                col += uAccent * ripple * 0.55;

                // Add vertical bands (subtle bright streaks)
                col += rimCol * bands * 0.35;

                // Breathing multiplier
                col *= breath * (0.85 + uEnergy * 0.35);

                // Slight gamma-ish lift for that "glowing" feel
                col = pow(col, vec3(0.92));

                gl_FragColor = vec4(col, 1.0);
            }
        """
    }
}
