package com.example.datavizar;

import static com.google.ar.core.ArCoreApk.InstallStatus.INSTALLED;
import static com.google.ar.core.ArCoreApk.InstallStatus.INSTALL_REQUESTED;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Config;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;

import common.helpers.CameraPermissionHelper;

import common.helpers.DepthSettings;
import common.helpers.DisplayRotationHelper;
import common.helpers.InstantPlacementSettings;
import common.helpers.SnackbarHelper;
import common.helpers.TapHelper;
import common.samplerender.Framebuffer;
import common.samplerender.GLError;
import common.samplerender.Mesh;
import common.samplerender.SampleRender;
import common.samplerender.Shader;
import common.samplerender.Texture;
import common.samplerender.VertexBuffer;
import common.samplerender.arcore.BackgroundRenderer;
import common.samplerender.arcore.PlaneRenderer;
import common.samplerender.arcore.SpecularCubemapFilter;

public class ARVisualizerActivity extends AppCompatActivity implements SampleRender.Renderer {

    private boolean mUserRequestedInstall = true;

    private boolean[] depthSettingsMenuDialogCheckboxes = new boolean[2];
    private DisplayRotationHelper displayRotationHelper;
    private final DepthSettings depthSettings = new DepthSettings();
    private GLSurfaceView surfaceView;
    private Session mSession;

    private boolean[] instantPlacementSettingsMenuDialogCheckboxes = new boolean[1];
    private SampleRender render;
    private TapHelper tapHelper;
    private static final String TAG = ARVisualizerActivity.class.getSimpleName();
    private final InstantPlacementSettings instantPlacementSettings = new InstantPlacementSettings();
    private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();

    @Override
    protected  void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_a_r_visualizer);
        surfaceView = findViewById(R.id.surfaceview);
        displayRotationHelper = new DisplayRotationHelper(this);

        tapHelper = new TapHelper(this);
        surfaceView.setOnTouchListener(tapHelper);

        render = new SampleRender(surfaceView, this, getAssets());
        mUserRequestedInstall= false;

        depthSettings.onCreate(this);
        instantPlacementSettings.onCreate(this);
        ImageButton settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        PopupMenu popup = new PopupMenu(ARVisualizerActivity.this, v);
                        popup.setOnMenuItemClickListener(ARVisualizerActivity.this::settingsMenuClick);
                        popup.inflate(R.menu.settings_menu);
                        popup.show();
                    }
                });
    }

    protected boolean settingsMenuClick(MenuItem item) {
        if (item.getItemId() == R.id.depth_settings) {
            launchDepthSettingsMenuDialog();
            return true;
        } else if (item.getItemId() == R.id.instant_placement_settings) {
            launchInstantPlacementSettingsMenuDialog();
            return true;
        }
        return false;
    }
    @Override
    protected void onResume() {
        super.onResume();

        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            CameraPermissionHelper.requestCameraPermission(this);
            return;
        }
        mSession = null;
        Exception exception = null;
        String message = null;
        try {
            if (mSession == null) {
                switch (ArCoreApk.getInstance().requestInstall(this, mUserRequestedInstall)) {
                    case INSTALLED:
                        mUserRequestedInstall = false;
                        break;
                    case INSTALL_REQUESTED:
                        mUserRequestedInstall = true;
                        return;
                }
            }
        mSession = new Session(this);
        } catch (UnavailableArcoreNotInstalledException
                 | UnavailableUserDeclinedInstallationException e) {
            message = "Please install ARCore";
            exception = e;
        } catch (UnavailableApkTooOldException e) {
            message = "Please update ARCore";
            exception = e;
        } catch (UnavailableSdkTooOldException e) {
            message = "Please update this app";
            exception = e;
        } catch (UnavailableDeviceNotCompatibleException e) {
            message = "This device does not support AR";
            exception = e;
        } catch (Exception e) {
            message = "Failed to create AR session";
            exception = e;
        }

        if (message != null) {
            messageSnackbarHelper.showError(this, message);
            Log.e(TAG, "Exception creating session", exception);
            return;
        }

        try {
            configureSession();
            mSession.resume();
        } catch (CameraNotAvailableException e) {
            messageSnackbarHelper.showError(this, "Camera not available. Try restarting the app.");
            mSession = null;
            return;
        }

        surfaceView.onResume();
        displayRotationHelper.onResume();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                    .show();
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this);
            }
            finish();
        }
    }

    @Override
    public void onSurfaceCreated(SampleRender render) {
        try {
            planeRender = new PlaneRenderer(render);
            backgroundRenderer = new BackgroundRenderer(render);
            virtualSceneFramebuffer = new Framebuffer(render, 1, 1);
            cubemapFilter = new SpecularCubemapFilter(render, CUBEMAP_RESOLUTION, CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES);
            dfgTexture = new Texture(render, Texture.Target.TEXTURE_2D, Texture.WrapMode.CLAMP_TO_EDGE, false);
            final int dfgResolution = 64;
            final int dfgChannels = 2;
            final int halfFloatSize = 2;

            ByteBuffer buffer = ByteBuffer.allocateDirect(dfgResolution*dfgResolution*dfgChannels*halfFloatSize);

            try (InputStream is = getAssets().open("models/dfg.raw")) {
                is.read(buffer.array());
            }

            GLES30.glBindTexture(GLES20.GL_TEXTURE2D, dfgTexture.getTextureId());
            GLError.maybeThrowGLException("Erro ao tentar aplicar textura dfg", "glBindTexture");
            GLES30.glTexImage2D(
                    GLES30.GL_TEXTURE_2D,
                    0,
                    GLES30.GL_RG16F,
                    dfgResolution,
                    dfgResolution,
                    0,
                    GLES30.GL_RG,
                    GLES30.GL_HALF_FLOAT,
                    buffer
            );
            GLError.maybeThrowGLException("Falha ao popular textura dfg", "glTexImage2d");
            pointCloudShader = Shader.createFromAssets(
                    render,
                    "shaders/point_cloud.vert",
                    "shaders/point_cloud.frag",
                    null).setVec4(
                            "u_Color",
                        new float[] {31.0f / 255.0f, 188.0f / 255.0f, 210.0f / 255.0f, 1.0f}
                    ).setFloat("u_PointSize", 5.0f);
            pointCloudVertexBuffer = new VertexBuffer(render, 4, null);
            final VertexBuffer[] pointCloudVertexBuffers = {pointCloudVertexBuffer};
            pointCloudMesh = new Mesh(render, Mesh.PrimitiveMode.POINTS, null, pointCloudVertexBuffers);
            virtualObjectAlbedoTexture = Texture.createFromAsset(
                    render,
                    "models/pawn_albedo.png",
                    Texture.WrapMode.CLAMP_TO_EDGE,
                    Texture.ColorFormat.SRGB
            );
            virtualObjectAlbedoInstantPlacementTexture = Texture.createFromAsset(
                    render,
                    "models/pawn_albedo_instant_placement.png",
                    Texture.WrapMode.CLAMP_TO_EDGE,
                    Texture.ColorFormat.SRGB
            );
            Texture virtualObjectPbrTexture = Texture.createFromAsset(
                    render,
                    "models/pawn_roughness_metallic_ao.png",
                    Texture.WrapMode.CLAMP_TO_EDGE,
                    Texture.ColorFormat.LINEAR
            );
            virtualObjectMesh = Mesh.createFromAsset(render, "models/pawn.obj");
            virtualObjectShader = Shader.createFromAssets(
                    render,
                    "shaders/environmental_hdr.vert",
                    "shaders/environmental_hdr.frag",
                    new HashMap<String, String>() {
                        {
                            put("NUMBER_OF_MIPMAP_LEVELS", Integer.toString(cubemapFilter.getNumberOfMipmapLevels()));
                        }
                    }
            )
            .setTexture("u_AlbedoTexture", virtualObjectAlbedoTexture)
            .setTexture("u_RoughnessMetallicAmbientOcclusionTexture", virtualObjectPbrTexture)
            .setTexture("u_Cubemap", cubemapFilter.getFilteredCubemapTexture())
            .setTexture("u_DfgTexture", dfgTexture);
        } catch (IOException e) {
            Log.e(TAG, "Não conseguiu ler um asset", e);
            messageSnackbarHelper.showError(this, "Não conseguiu ler um asset" + e);
        }
    }

    @Override
    public void onSurfaceChanged(SampleRender render, int width, int height) {
        displayRotationHelper.onSurfaceChanged(width, height);
        virtualSceneFramebuffer.resize(width, height);
    }

    @Override
    public void onDrawFrame(SampleRender render) {
        if (mSession == null) {
            return;
        }

        if(!hasSetTextureNames) {
            mSession.setCameraTextureNames(new int[] {backgroundRenderer.getCameraColorTexture().getTextureId()});
            hasSetTextureNames = true;
        }

        displayRotationHelper.updateSessionIfNeeded(mSession);
        Frame frame;
        try {
            frame = mSession.update();
        } catch (CameraNotAvailableException e) {
            Log.e(TAG, "Camera não disponível em drawFrame", e);
            messageSnackbarHelper.showError(this, "Camera não disponível, reinicie o aplicativo");
            return;
        }
        Camera camera = frame.getCamera();
    }

    private void configureSession() {
        Config config = mSession.getConfig();
        config.setLightEstimationMode(Config.LightEstimationMode.ENVIRONMENTAL_HDR);
        if (mSession.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            config.setDepthMode(Config.DepthMode.AUTOMATIC);
        } else {
            config.setDepthMode(Config.DepthMode.DISABLED);
        }
        if (instantPlacementSettings.isInstantPlacementEnabled()) {
            config.setInstantPlacementMode(Config.InstantPlacementMode.LOCAL_Y_UP);
        } else {
            config.setInstantPlacementMode(Config.InstantPlacementMode.DISABLED);
        }
        mSession.configure(config);
    }

    private void launchDepthSettingsMenuDialog() {
        // Retrieves the current settings to show in the checkboxes.
        resetSettingsMenuDialogCheckboxes();

        // Shows the dialog to the user.
        Resources resources = getResources();
        if (mSession.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            // With depth support, the user can select visualization options.
            new AlertDialog.Builder(this)
                    .setTitle(R.string.options_title_with_depth)
                    .setMultiChoiceItems(
                            resources.getStringArray(R.array.depth_options_array),
                            depthSettingsMenuDialogCheckboxes,
                            (DialogInterface dialog, int which, boolean isChecked) ->
                                    depthSettingsMenuDialogCheckboxes[which] = isChecked)
                    .setPositiveButton(
                            R.string.done,
                            (DialogInterface dialogInterface, int which) -> applySettingsMenuDialogCheckboxes())
                    .setNegativeButton(
                            android.R.string.cancel,
                            (DialogInterface dialog, int which) -> resetSettingsMenuDialogCheckboxes())
                    .show();
        } else {
            // Without depth support, no settings are available.
            new AlertDialog.Builder(this)
                    .setTitle(R.string.options_title_without_depth)
                    .setPositiveButton(
                            R.string.done,
                            (DialogInterface dialogInterface, int which) -> applySettingsMenuDialogCheckboxes())
                    .show();
        }
    }

    private void launchInstantPlacementSettingsMenuDialog() {
        resetSettingsMenuDialogCheckboxes();
        Resources resources = getResources();
        new AlertDialog.Builder(this)
                .setTitle(R.string.options_title_instant_placement)
                .setMultiChoiceItems(
                        resources.getStringArray(R.array.instant_placement_options_array),
                        instantPlacementSettingsMenuDialogCheckboxes,
                        (DialogInterface dialog, int which, boolean isChecked) ->
                                instantPlacementSettingsMenuDialogCheckboxes[which] = isChecked)
                .setPositiveButton(
                        R.string.done,
                        (DialogInterface dialogInterface, int which) -> applySettingsMenuDialogCheckboxes())
                .setNegativeButton(
                        android.R.string.cancel,
                        (DialogInterface dialog, int which) -> resetSettingsMenuDialogCheckboxes())
                .show();
    }

    private void resetSettingsMenuDialogCheckboxes() {
        depthSettingsMenuDialogCheckboxes[0] = depthSettings.useDepthForOcclusion();
        depthSettingsMenuDialogCheckboxes[1] = depthSettings.depthColorVisualizationEnabled();
        instantPlacementSettingsMenuDialogCheckboxes[0] =
                instantPlacementSettings.isInstantPlacementEnabled();
    }

    private void applySettingsMenuDialogCheckboxes() {
        depthSettings.setUseDepthForOcclusion(depthSettingsMenuDialogCheckboxes[0]);
        depthSettings.setDepthColorVisualizationEnabled(depthSettingsMenuDialogCheckboxes[1]);
        instantPlacementSettings.setInstantPlacementEnabled(
                instantPlacementSettingsMenuDialogCheckboxes[0]);
        configureSession();
    }
}
