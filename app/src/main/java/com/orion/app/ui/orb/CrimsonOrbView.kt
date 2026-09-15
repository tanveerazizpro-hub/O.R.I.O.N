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
 * Cinematic 3D plasma orb (OpenGL ES 2.0).
 *
 * Layers (all inside the fragment shader):
 *  - Multi-octave plasma noise (organic, flows across the surface)
 *  - Blazing white-hot core
 *  - Strong fresnel rim so the silhouette reads as energy, not a ball
 *  - Time-based breathing
 *
 * Low-end optimization:
 *  - GLES 2.0 only
 *  - 30fps cap
 *  - 40x24 sphere (~1900 tris)
 *  - No textures, no lights, no external libraries
 */
class CrimsonOrbView(context: Context) : GLSurfaceView(context) {

    private val orbRenderer = CinematicOrbRenderer()

    init {
        setEGLContextClientVersion(2)
        setRenderer(orbRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun setAccent(r: Float, g: Float, b: Float) {
        orbRenderer.accentR = r
        orbRenderer.accentG = g
        orbRenderer.accentB = b
    }

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
    private val minFrameInterval = 33_333_333L

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.039f, 0.055f, 0.101f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

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
            0f, 0f, 3.2f,
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
        Matrix.rotateM(modelMatrix, 0, t * 12f, 0f, 1f, 0f)
        Matrix.rotateM(modelMatrix, 0, sin(t * 0.45f) * 10f, 1f, 0f, 0f)

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
         * Plasma orb shader.
         *
         *  - Hot white core (pow on facing)
         *  - Multi-octave plasma noise (fbm) that flows along the surface
         *  - Strong fresnel rim (bright energy edge)
         *  - Tone mapping to keep highlights hot without clipping
         */
        private const val FRAGMENT_SHADER = """
            precision highp float;

            uniform vec3  uAccent;
            uniform float uTime;
            uniform float uEnergy;
            varying vec3 vNormal;
            varying vec3 vWorldPos;

            // 3D hash → pseudo-random float
            float hash(vec3 p) {
                p = fract(p * vec3(0.1031, 0.1030, 0.0973));
                p += dot(p, p.yzx + 33.33);
                return fract((p.x + p.y) * p.z);
            }

            // Smooth 3D value noise
            float noise(vec3 x) {
                vec3 i = floor(x);
                vec3 f = fract(x);
                f = f * f * (3.0 - 2.0 * f);
                return mix(
                    mix(mix(hash(i + vec3(0.0,0.0,0.0)), hash(i + vec3(1.0,0.0,0.0)), f.x),
                        mix(hash(i + vec3(0.0,1.0,0.0)), hash(i + vec3(1.0,1.0,0.0)), f.x), f.y),
                    mix(mix(hash(i + vec3(0.0,0.0,1.0)), hash(i + vec3(1.0,0.0,1.0)), f.x),
                        mix(hash(i + vec3(0.0,1.0,1.0)), hash(i + vec3(1.0,1.0,1.0)), f.x), f.y),
                    f.z
                );
            }

            // Fractal Brownian Motion — 3 octaves of noise
            float fbm(vec3 p) {
                float v = 0.0;
                v += 0.500 * noise(p);
                v += 0.250 * noise(p * 2.03);
                v += 0.125 * noise(p * 4.07);
                return v;
            }

            void main() {
                vec3 N = normalize(vNormal);
                vec3 V = normalize(-vWorldPos);

                // Facing: 1 at camera-facing center, 0 at silhouette edge
                float facing = max(dot(N, V), 0.0);

                // Bright fresnel rim
                float rim = pow(1.0 - facing, 2.8);

                // Hot core
                float core = pow(facing, 3.5);

                // Plasma texture — 3 layers at different scales and speeds
                float n1 = fbm(N * 2.5 + vec3(0.0, uTime * 0.15, 0.0));
                float n2 = fbm(N * 5.0 - vec3(uTime * 0.22, 0.0, uTime * 0.10));
                float n3 = noise(N * 9.0 + vec3(uTime * 0.35, uTime * 0.20, 0.0));
                float plasma = n1 * 0.50 + n2 * 0.30 + n3 * 0.20;
                plasma = smoothstep(0.20, 0.80, plasma);

                // Breathing
                float breath = 0.92 + 0.08 * sin(uTime * 2.2);

                // Colors
                vec3 accent  = uAccent;
                vec3 deepCol = uAccent * 0.15;
                vec3 hotCol  = vec3(1.0, 0.97, 0.92);

                // Base: dark at edges → accent toward center
                vec3 color = mix(deepCol, accent, pow(facing, 1.2));

                // Plasma adds accent-tinted brightness
                color += accent * plasma * (0.55 + uEnergy * 0.75);

                // Hot core
                color += hotCol * core * 2.2;

                // Fresnel rim (bright energy edge)
                color += accent * rim * 2.8;

                // Breathing + energy
                color *= breath * (0.85 + uEnergy * 0.50);

                // Tone map: keeps highlights hot, prevents washout
                color = color / (color + vec3(0.85));

                // Slight gamma lift
                color = pow(color, vec3(0.85));

                gl_FragColor = vec4(color, 1.0);
            }
        """
    }
}
