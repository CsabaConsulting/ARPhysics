package dev.csaba.arphysics;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.snackbar.Snackbar;
import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.FatalException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Camera;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.Sun;
import com.google.ar.sceneform.collision.Ray;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.ux.ArFragment;

import java.util.List;
import javax.vecmath.Vector3f;

public class MainActivity extends AppCompatActivity {
    enum AppState {
        INITIAL,
        TOWER_PLACED,
        BALL_HURDLED
    }

    private static final String TAG = "MainActivity";

    private static final float WIDTH = 0.2f;
    private static final float HEIGHT = 0.05f;
    private static final float DEPTH = 0.025f;
    private static final float RADIUS = HEIGHT;
    private static final float CONVEX_MARGIN = 0.0025f;

    private ArFragment fragment;
    private PointerDrawable pointer = new PointerDrawable();
    private boolean isTracking;
    private boolean isHitting;

    private PhysicsController physicsController;
    private AppState appState = AppState.INITIAL;

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
        int slowMotion = preferences.getInt("slow_motion", 1);
        int numFloors = preferences.getInt("num_floors", 10);

        return new ModelParameters(
            numFloors,
            gravityInt / 10.0f,
            slabRestitutionInt / 100.0f,
            slabFrictionInt / 100.0f,
            slabDensityInt * 10.0f,
            ballRestitutionInt / 100.0f,
            ballFrictionInt / 100.0f,
            ballDensityInt * 100.0f,
            WIDTH,
            HEIGHT,
            DEPTH,
            RADIUS,
            CONVEX_MARGIN,
            slowMotion
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fragment = (ArFragment)
                getSupportFragmentManager().findFragmentById(R.id.sceneform_fragment);

        assert fragment != null;
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
            int numFloors = getModelParameters().getNumFloors();
            for (int i = 0; i < numFloors; i++) {
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
                    float margin = CONVEX_MARGIN;
                    Vector3 pos = new Vector3(
                        even ? 0.0f : displacement,
                        margin + (HEIGHT + margin) * i,
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
            appState = AppState.TOWER_PLACED;
        });
    }

    private void hurdleBall(Vector3 hurdleVector, Vector3 cameraPosition, ArSceneView arSceneView, Anchor anchor) {
        if (physicsController == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.WarningDialogStyle);
            builder.setMessage(getString(R.string.step123_details))
                .setTitle(getString(R.string.step123_title))
                .setPositiveButton("OK", null);
            AlertDialog dialog = builder.create();
            dialog.show();
            return;
        }

        AnchorNode anchorNode = new AnchorNode(anchor);
        Scene scene = arSceneView.getScene();
        anchorNode.setParent(scene);

        Color ballColor = new Color(android.graphics.Color.RED);
        MaterialFactory.makeOpaqueWithColor(this, ballColor)
            .thenAccept(material -> {
                ModelRenderable renderable = ShapeFactory.makeSphere(
                        RADIUS,
                        cameraPosition,
                        material
                );

                Node node = new Node();
                node.setParent(anchorNode);
                node.setRenderable(renderable);
                node.setLocalPosition(new Vector3(0, 0, 0));

                // The camera look direction is the hurdle inertia, maybe scaling needed
                physicsController.addBallRigidBody(
                    node,
                    new Vector3f(cameraPosition.x, cameraPosition.y, cameraPosition.z),
                    new Vector3f(hurdleVector.x / 2, hurdleVector.y / 2, hurdleVector.z / 2)
                );
                appState = AppState.BALL_HURDLED;
            });
    }

    private void addObject(boolean isHurdle, ImageView iconButton) {
        if (isHurdle && appState != AppState.TOWER_PLACED) {
            String text = getString(R.string.tower_before_hurdle);
            Snackbar.make(findViewById(android.R.id.content),
                    text, Snackbar.LENGTH_SHORT).show();
            return;
        }
        if (!isHurdle && appState != AppState.INITIAL) {
            String text = getString(R.string.tower_after_initial);
            Snackbar.make(findViewById(android.R.id.content),
                    text, Snackbar.LENGTH_SHORT).show();
            return;
        }

        ArSceneView arSceneView = fragment.getArSceneView();
        Frame frame = fragment.getArSceneView().getArFrame();
        android.graphics.Point pt = getScreenCenter();
        if (frame != null) {
            if (isHurdle) {
                Camera camera = fragment.getArSceneView().getScene().getCamera();
                Ray gazeRay = camera.screenPointToRay(pt.x, pt.y);
                Vector3 ourPosition = gazeRay.getOrigin();
                Vector3 gazeDirection = gazeRay.getDirection();

                // Add an Anchor in front of the camera
                Session session = arSceneView.getSession();
                float[] pos = { 0, 0, -1 };
                float[] rotation = { 0, 0, 0, 1 };
                assert session != null;
                Anchor anchor =  session.createAnchor(new Pose(pos, rotation));

                hurdleBall(gazeDirection, ourPosition, arSceneView, anchor);
            } else {
                List<HitResult> hits = frame.hitTest(pt.x, pt.y);
                for (HitResult hit : hits) {
                    Trackable trackable = hit.getTrackable();
                    if (trackable instanceof Plane &&
                            ((Plane) trackable).isPoseInPolygon(hit.getHitPose())) {
                        Anchor hitAnchor = null;
                        try {
                            hitAnchor = hit.createAnchor();
                        }
                        catch (FatalException ex) {
                            Log.d(TAG, "Unexpected error while trying to create anchor for the tower");
                        }
                        if (hitAnchor != null) {
                            physicsController = new PhysicsController(getModelParameters());
                            iconButton.setEnabled(false);
                            buildTower(arSceneView, hitAnchor);
                            iconButton.setEnabled(true);
                        }
                    }
                    break;
                }
            }
        }
    }

    private void clearScene() {
        if (appState == AppState.INITIAL) {
            String text = getString(R.string.already_clean_scene);
            Snackbar.make(findViewById(android.R.id.content),
                    text, Snackbar.LENGTH_SHORT).show();

            return;
        }

        physicsController = null;
        // Clear the SceneForm scene
        fragment.getArSceneView().getScene().callOnHierarchy(node -> {
            if (node instanceof Camera || node instanceof Sun) {
                return;
            }
            node.setParent(null);
            if (node instanceof AnchorNode) {
                Anchor anchorNode = ((AnchorNode) node).getAnchor();
                if (anchorNode != null) {
                    anchorNode.detach();
                }
            }
        });
        appState = AppState.INITIAL;
    }

    private void displayHelp() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.InfoDialogStyle);
        builder.setMessage(getString(R.string.how_to_play))
                .setTitle(getString(R.string.quick_help))
                .setPositiveButton("OK", null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void initializeGallery() {
        ImageView restartIcon = findViewById(R.id.restartIcon);
        restartIcon.setOnClickListener(view -> clearScene());

        ImageView settingsIcon = findViewById(R.id.settingsIcon);
        settingsIcon.setOnClickListener(view -> {
            if (appState == AppState.INITIAL) {
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
            } else {
                String text = getString(R.string.settings_only_while_initial);
                Snackbar.make(findViewById(android.R.id.content),
                        text, Snackbar.LENGTH_SHORT).show();
            }

            /*
            ModelParameters modelParameters = getModelParameters();
            String infoText = String.format(Locale.getDefault(),
                    "Gravity = %.2f m/s^2\n", modelParameters.getGravity());
            infoText += String.format(Locale.getDefault(),
                    "Slab restitution = %.2f\n", modelParameters.getSlabRestitution());
            infoText += String.format(Locale.getDefault(),
                    "Slab friction = %.2f\n", modelParameters.getSlabFriction());
            infoText += String.format(Locale.getDefault(),
                    "Slab density = %.3f kg/m^3\n", modelParameters.getSlabDensity());
            infoText += String.format(Locale.getDefault(),
                    "Ball restitution = %.2f\n", modelParameters.getBallFriction());
            infoText += String.format(Locale.getDefault(),
                    "Ball friction = %.2f\n", modelParameters.getBallFriction());
            infoText += String.format(Locale.getDefault(),
                    "Ball density = %.3f kg/m^3\n", modelParameters.getBallDensity());

            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.InfoDialogStyle);
            builder.setMessage(infoText)
                    .setTitle("Model parameters")
                    .setPositiveButton("OK", null);
            AlertDialog dialog = builder.create();
            dialog.show();
           */
        });

        ImageView pantheonIcon = findViewById(R.id.pantheonIcon);
        pantheonIcon.setOnClickListener(view -> addObject(false, pantheonIcon));

        ImageView aimIcon = findViewById(R.id.aimIcon);
        aimIcon.setOnClickListener(view -> addObject(true, null));

        ImageView step1Icon = findViewById(R.id.step1Icon);
        step1Icon.setOnClickListener(view -> displayHelp());

        ImageView step2Icon = findViewById(R.id.step2Icon);
        step2Icon.setOnClickListener(view -> displayHelp());
    }
}
