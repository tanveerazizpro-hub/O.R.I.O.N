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

        buildSphere(40, 24)

        startNanos = System.nanoTime()
        lastFrameNanos = startNanos
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(projMatrix, 0, 45f, aspect, 0.1f, 100f)
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 3.2f, 0f, 0f, 0f, 0f, 1f, 0f)
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
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, indexBuffer)

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
                vertices[vi] = nx; vertices[vi+1] = ny; vertices[vi+2] = nz
                normals[vi]  = nx; normals[vi+1]  = ny; normals[vi+2]  = nz
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
                indices[ii++] = a.toShort(); indices[ii++] = c.toShort(); indices[ii++] = b.toShort()
                indices[ii++] = b.toShort(); indices[ii++] = c.toShort(); indices[ii++] = d.toShort()
            }
        }

        indexCount = indices.size

        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
            .apply { put(vertices); position(0) }
        normalBuffer = ByteBuffer.allocateDirect(normals.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
            .apply { put(normals); position(0) }
        indexBuffer = ByteBuffer.allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder()).asShortBuffer()
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

        private const val FRAGMENT_SHADER = """
            precision highp float;

            uniform vec3  uAccent;
            uniform float uTime;
            uniform float uEnergy;
            varying vec3 vNormal;
            varying vec3 vWorldPos;

            float hash(vec3 p) {
                p = fract(p * vec3(0.1031, 0.1030, 0.0973));
                p += dot(p, p.yzx + 33.33);
                return fract((p.x + p.y) * p.z);
            }

            float noise(vec3 x) {
                vec3 i = floor(x);
                vec3 f = fract(x);
                f = f * f * (3.0 - 2.0 * f);
                return mix(
                    mix(mix(hash(i), hash(i + vec3(1,0,0)), f.x),
                        mix(hash(i + vec3(0,1,0)), hash(i + vec3(1,1,0)), f.x), f.y),
                    mix(mix(hash(i + vec3(0,0,1)), hash(i + vec3(1,0,1)), f.x),
                        mix(hash(i + vec3(0,1,1)), hash(i + vec3(1,1,1)), f.x), f.y),
                    f.z);
            }

            float fbm(vec3 p) {
                return 0.55 * noise(p) + 0.28 * noise(p * 2.07) + 0.17 * noise(p * 4.13);
            }

            void main() {
                vec3 N = normalize(vNormal);
                vec3 V = normalize(-vWorldPos);
                float facing = max(dot(N, V), 0.0);

                // Silhouette rim glow (strong at edges)
                float rim = pow(1.0 - facing, 3.5);

                // Tiny hot core (only at very center)
                float core = pow(facing, 30.0);

                // Base body — dark at edges, accent toward camera
                vec3 color = uAccent * (0.18 + 0.72 * facing);

                // Plasma modulation (brightness only, keeps hue)
                float plasma = smoothstep(0.25, 0.75, fbm(N * 4.5 + vec3(0.0, uTime * 0.15, 0.0)));
                color *= mix(0.85, 1.15, plasma);

                // Rim — accent-colored, adds saturation at edge
                color += uAccent * rim * 1.0;

                // Tiny hot core — small white dot, doesn't wash whole sphere
                color += vec3(1.0, 0.97, 0.92) * core * 0.35;

                // Breathing
                color *= 0.94 + 0.06 * sin(uTime * 2.0);

                // Clamp — no tone map, so accent hue survives
                gl_FragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
            }
        """
    }
}
