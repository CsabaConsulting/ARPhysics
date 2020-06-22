package dev.csaba.arphysics;

import android.content.SharedPreferences;
import android.os.Bundle;

import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Camera;
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

import javax.vecmath.Vector3f;

public class MainActivity extends AppCompatActivity {
    private static final int NUM_FLOORS = 10;
    private static final float WIDTH = 0.2f;
    private static final float HEIGHT = 0.05f;
    private static final float DEPTH = 0.01f;
    private static final float RADIUS = HEIGHT;
    private static final float CONVEX_MARGIN = 0.0005f;

    private ArFragment fragment;
    private PointerDrawable pointer = new PointerDrawable();
    private boolean isTracking;
    private boolean isHitting;

    private PhysicsController physicsController;

    ModelParameters getModelParameters() {
        SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        int gravityInt = preferences.getInt("gravity", 100);
        int slabRestitutionInt = preferences.getInt("slab_restitution", 0);
        int slabFrictionInt = preferences.getInt("slab_friction", 100);
        int slabDensityInt = preferences.getInt("slab_density", 50);
        int ballRestitutionInt = preferences.getInt("ball_restitution", 0);
        int ballFrictionInt = preferences.getInt("ball_friction", 50);
        int ballDensityInt = preferences.getInt("ball_density", 80);

        return new ModelParameters(
            NUM_FLOORS,
            gravityInt / 10.0f,
            slabRestitutionInt / 100.0f,
            slabFrictionInt / 100.0f,
            slabDensityInt / 100.0f,
            ballRestitutionInt / 100.0f,
            ballFrictionInt / 100.0f,
            ballDensityInt / 100.0f,
            WIDTH,
            HEIGHT,
            DEPTH,
            RADIUS,
            CONVEX_MARGIN
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

        if (physicsController != null) {
            physicsController.updatePhysics();
        }

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

        Color slabColor = new Color(0xFF593C1F);  // Brown RGB: 89, 60, 31
        MaterialFactory.makeOpaqueWithColor(this, slabColor)
                .thenAccept(material -> {
            for (int i = 0; i < NUM_FLOORS; i++) {
                boolean even = i % 2 == 0;
                for (int j = -1; j <= 1; j += 2) {
                    Vector3 box = new Vector3(even ? WIDTH : DEPTH, HEIGHT, even ? DEPTH: WIDTH);
                    ModelRenderable renderable = ShapeFactory.makeCube(
                        box,
                        new Vector3(0.0f, 0.0f, 0.0f),
                        material
                    );

                    Node node = new Node();
                    node.setParent(anchorNode);
                    node.setRenderable(renderable);
                    float displacement = (WIDTH - 2 * DEPTH) / 2 * j;
                    Vector3 pos = new Vector3(
                        even ? 0.0f : displacement,
                        CONVEX_MARGIN + (HEIGHT + 2 * CONVEX_MARGIN) * i,
                        even ? displacement : 0.0f
                    );
                    node.setLocalPosition(pos);

                    int index = i * 2 + (j < 0 ? 0 : 1);
                    physicsController.addSlabRigidBody(
                        index,
                        node,
                        new Vector3f(box.x / 2, box.y / 2, box.z / 2),
                        new Vector3f(pos.x, pos.y, pos.z)
                    );
                }
            }
        });
    }

    private void hurdleBall(Vector3 hurdle, Vector3 cameraLook, ArSceneView arSceneView, Anchor anchor) {
        if (physicsController == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.WarningDialogStyle);
            builder.setMessage(getString(R.string.step123_details))
                .setTitle(getString(R.string.step123_title))
                .setPositiveButton("OK", null);
            AlertDialog dialog = builder.create();
            dialog.show();
        }

        AnchorNode anchorNode = new AnchorNode(anchor);
        Scene scene = arSceneView.getScene();
        anchorNode.setParent(scene);

        Color ballColor = new Color(android.graphics.Color.RED);
        MaterialFactory.makeOpaqueWithColor(this, ballColor)
            .thenAccept(material -> {
                ModelRenderable renderable = ShapeFactory.makeSphere(
                        RADIUS,
                        cameraLook,
                        material
                );

                Node node = new Node();
                node.setParent(anchorNode);
                node.setRenderable(renderable);
                node.setLocalPosition(new Vector3(0, 0, 0));

                // The camera look direction is the hurdle inertia, maybe scaling needed
                physicsController.addBallRigidBody(
                    node,
                    new Vector3f(hurdle.x, hurdle.y, hurdle.z),
                    new Vector3f(hurdle.x, hurdle.y, hurdle.z)
                );
            });
    }

    private void addObject(boolean isHurdle) {
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
                    Anchor hitAnchor = hit.createAnchor();
                    if (isHurdle) {
                        // Pose endPose = hit.getHitPose();
                        Camera camera = fragment.getArSceneView().getScene().getCamera();
                        Vector3 look = camera.getForward();
                        hurdleBall(look, look, arSceneView, hitAnchor);
                    } else {
                        physicsController = new PhysicsController(getModelParameters());
                        buildTower(arSceneView, hitAnchor);
                    }
                    break;
                }
            }
        }
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
            addObject(false);
        });

        ImageView aimIcon = findViewById(R.id.aimIcon);
        aimIcon.setOnClickListener(view -> {
            addObject(true);
        });
    }
}
