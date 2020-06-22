package dev.csaba.arphysics;

import android.content.SharedPreferences;
import android.os.Bundle;

import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.ux.ArFragment;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import android.view.View;
import android.widget.ImageView;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int NUM_STORIES = 10;

    private ArFragment fragment;
    private PointerDrawable pointer = new PointerDrawable();
    private boolean isTracking;
    private boolean isHitting;

    ModelParameters getModelParameters() {
        SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        int gravityInt = preferences.getInt("gravity", 100);
        int slabRestitutionInt = preferences.getInt("slab_restitution", 50);
        int slabFrictionInt = preferences.getInt("slab_friction", 50);
        int slabDensityInt = preferences.getInt("slab_density", 50);
        int ballRestitutionInt = preferences.getInt("ball_restitution", 50);
        int ballFrictionInt = preferences.getInt("ball_friction", 50);
        int ballDensityInt = preferences.getInt("ball_density", 50);

        return new ModelParameters(
            gravityInt / 10.0f,
            slabRestitutionInt / 100.0f,
            slabFrictionInt / 100.0f,
            slabDensityInt / 100.0f,
            ballRestitutionInt / 100.0f,
            ballFrictionInt / 100.0f,
            ballDensityInt / 100.0f
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fragment = (ArFragment)
                getSupportFragmentManager().findFragmentById(R.id.sceneform_fragment);

        fragment.getArSceneView().getScene().addOnUpdateListener(frameTime -> {
            fragment.onUpdate(frameTime);
            onUpdate();
        });

        initializeGallery();
    }

    private void onUpdate() {
        boolean trackingChanged = updateTracking();
        View contentView = findViewById(android.R.id.content);
        if (trackingChanged) {
            if (isTracking) {
                contentView.getOverlay().add(pointer);
            } else {
                contentView.getOverlay().remove(pointer);
            }
            contentView.invalidate();
        }

        if (isTracking) {
            boolean hitTestChanged = updateHitTest();
            if (hitTestChanged) {
                pointer.setEnabled(isHitting);
                contentView.invalidate();
            }
        }
    }

    private boolean updateTracking() {
        Frame frame = fragment.getArSceneView().getArFrame();
        boolean wasTracking = isTracking;
        isTracking = frame != null &&
                frame.getCamera().getTrackingState() == TrackingState.TRACKING;
        return isTracking != wasTracking;
    }

    private boolean updateHitTest() {
        Frame frame = fragment.getArSceneView().getArFrame();
        android.graphics.Point pt = getScreenCenter();
        List<HitResult> hits;
        boolean wasHitting = isHitting;
        isHitting = false;
        if (frame != null) {
            hits = frame.hitTest(pt.x, pt.y);
            for (HitResult hit : hits) {
                Trackable trackable = hit.getTrackable();
                if (trackable instanceof Plane &&
                        ((Plane) trackable).isPoseInPolygon(hit.getHitPose())) {
                    isHitting = true;
                    break;
                }
            }
        }
        return wasHitting != isHitting;
    }

    private android.graphics.Point getScreenCenter() {
        View vw = findViewById(android.R.id.content);
        return new android.graphics.Point(vw.getWidth() / 2, vw.getHeight() / 2);
    }

    private void buildTower(ArSceneView arSceneView, Anchor anchor) {
        AnchorNode anchorNode = new AnchorNode(anchor);
        Scene scene = arSceneView.getScene();
        anchorNode.setParent(scene);

        Color brown = new Color(89, 60, 31);
        for (int i = 0; i < NUM_STORIES; i++) {
            final int iLambda = i;
            MaterialFactory.makeOpaqueWithColor(this, brown)
                    .thenAccept(material -> {
                boolean evenStory = iLambda % 2 == 0;
                for (int j = -1; j <= 1; j += 2) {
                    ModelRenderable renderable = ShapeFactory.makeCube(
                        new Vector3(evenStory ? 0.2f : 0.01f, 0.05f, evenStory ? 0.01f: 0.2f),
                        new Vector3(0.0f, 0.0f, 0.0f),
                        material
                    );

                    Node node = new Node();  // TODO: PhysicsNode?
                    node.setParent(anchorNode);
                    node.setRenderable(renderable);
                    float displacement = (0.2f - 2 * 0.01f) / 2 * j;
                    Vector3 pos = new Vector3(
                        evenStory ? 0.0f : displacement,
                        0.05f * iLambda,
                        evenStory ? displacement : 0.0f
                    );
                    node.setLocalPosition(pos);
                }
            });
        }
    }

    private void eyeTower() {
        ArSceneView arSceneView = fragment.getArSceneView();
        Frame frame = fragment.getArSceneView().getArFrame();
        android.graphics.Point pt = getScreenCenter();
        List<HitResult> hits;
        if (frame != null) {
            hits = frame.hitTest(pt.x, pt.y);
            for (HitResult hit : hits) {
                Trackable trackable = hit.getTrackable();
                if (trackable instanceof Plane &&
                    ((Plane) trackable).isPoseInPolygon(hit.getHitPose()))
                {
                    buildTower(arSceneView, hit.createAnchor());
                    break;
                }
            }
        }
    }

    private void hurdleBall() {
        ;
    }

    private void initializeGallery() {
        ImageView settingsIcon = findViewById(R.id.settingsIcon);
        settingsIcon.setOnClickListener(view -> {
            // Intent intent = new Intent(this, SettingsActivity.class);
            // startActivity(intent);

            ModelParameters modelParameters = getModelParameters();
            String infoText = String.format("Gravity = %.2f m/s^2\n", modelParameters.getGravity());
            infoText += String.format("Slab restitution = %.2f\n", modelParameters.getSlabRestitution());
            infoText += String.format("Slab friction = %.2f\n", modelParameters.getSlabFriction());
            infoText += String.format("Slab density = %.2f 10^3 kg/m^3\n", modelParameters.getSlabDensity());
            infoText += String.format("Ball restitution = %.2f\n", modelParameters.getBallFriction());
            infoText += String.format("Ball friction = %.2f\n", modelParameters.getBallFriction());
            infoText += String.format("Ball density = %.2f 10^3 kg/m^3\n", modelParameters.getBallDensity());

            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.InfoDialogStyle);
            builder.setMessage(infoText).setTitle("Model parameters").setPositiveButton("OK", null);
            AlertDialog dialog = builder.create();
            dialog.show();
        });

        ImageView pantheonIcon = findViewById(R.id.pantheonIcon);
        pantheonIcon.setOnClickListener(view -> {
            eyeTower();
        });

        ImageView aimIcon = findViewById(R.id.aimIcon);
        aimIcon.setOnClickListener(view -> {
            hurdleBall();
        });
    }
}
