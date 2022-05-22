package dev.csaba.arphysics;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
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
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.FatalException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Camera;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.Material;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import java.util.EnumSet;
import java.util.List;
import javax.vecmath.Vector3f;

import dev.csaba.arphysics.engine.JBulletController;

public class MainActivity extends AppCompatActivity implements Node.TransformChangedListener {
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

    private JBulletController jBulletController;
    private AppState appState = AppState.INITIAL;
    private SimulationScenario simulationScenario = SimulationScenario.PlankTower;
    private TransformableNode cylinderNode;

    ModelParameters getModelParameters() {
        SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        int gravityInt = preferences.getInt("gravity", 100);
        int plankRestitutionInt = preferences.getInt("plank_restitution", 0);
        int plankFrictionInt = preferences.getInt("plank_friction", 100);
        int plankDensityInt = preferences.getInt("plank_density", 50);
        int ballRestitutionInt = preferences.getInt("ball_restitution", 0);
        int ballFrictionInt = preferences.getInt("ball_friction", 50);
        int ballDensityInt = preferences.getInt("ball_density", 80);
        int slowMotion = preferences.getInt("slow_motion", 1);
        int numFloors = preferences.getInt("num_floors", 10);

        return new ModelParameters(
            numFloors,
            gravityInt / 10.0f,
            plankRestitutionInt / 100.0f,
            plankFrictionInt / 100.0f,
            plankDensityInt * 10.0f,
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

        Intent startingIntent = getIntent();
        simulationScenario = (SimulationScenario)startingIntent.getSerializableExtra(ChooserActivity.SIMULATION_SCENARIO);

        initializeGallery();
    }

    public void onDestroy() {
        clearScene(true);
        super.onDestroy();
    }

    private void onUpdate() {
        boolean trackingChanged = updateTracking();

        if (jBulletController != null && appState != AppState.INITIAL) {
            Vector3f cylinderPosition = null;
            if (simulationScenario == SimulationScenario.CollisionBox && cylinderNode != null) {
                Vector3 position = cylinderNode.getLocalPosition();
                cylinderPosition = new Vector3f(position.x, position.y, position.z);
            }
            jBulletController.updatePhysics(cylinderPosition);
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
        Point pt = getScreenCenter();
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

    private Point getScreenCenter() {
        View vw = findViewById(android.R.id.content);
        return new Point(vw.getWidth() / 2, vw.getHeight() / 2);
    }

    private void buildTower(Material material, AnchorNode anchorNode) {
        int numFloors = getModelParameters().getNumFloors();
        for (int i = 0; i < numFloors; i++) {
            boolean even = i % 2 == 0;
            for (int j = -1; j <= 1; j += 2) {
                Vector3 box = new Vector3(even ? WIDTH : DEPTH, HEIGHT, even ? DEPTH: WIDTH);
                ModelRenderable renderable = ShapeFactory.makeCube(
                    box,
                    new Vector3(0, 0, 0),
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
                jBulletController.addPlankRigidBody(
                    index,
                    node,
                    new Vector3f(box.x / 2, box.y / 2, box.z / 2),
                    new Vector3f(pos.x, pos.y, pos.z)
                );
            }
        }
    }

    private void buildPlankMatrix(Material material, AnchorNode anchorNode) {
        int numFloors = getModelParameters().getNumFloors();
        int numPlanks = numFloors * numFloors;
        float spacing = 1.0f / (numFloors + 1);
        for (int i = 0; i < numPlanks; i++) {
            Vector3 box = new Vector3(HEIGHT, WIDTH, HEIGHT);
            ModelRenderable renderable = ShapeFactory.makeCube(
                box,
                new Vector3(0, 0, 0),
                material
            );

            Node node = new Node();
            node.setParent(anchorNode);
            node.setRenderable(renderable);
            int xIndex = i % numFloors + 1;
            int zIndex = i / numFloors + 1;
            float xPos = xIndex * spacing - 0.5f;
            float zPos = zIndex * spacing - 0.5f;
            Vector3 pos = new Vector3(xPos, CONVEX_MARGIN, zPos);
            node.setLocalPosition(pos);

            jBulletController.addPlankRigidBody(
                i,
                node,
                new Vector3f(box.x / 2, box.y / 2, box.z / 2),
                new Vector3f(pos.x, pos.y, pos.z)
            );
        }
    }

    private void spawnStructure(ArSceneView arSceneView, Anchor anchor) {
        AnchorNode anchorNode = new AnchorNode(anchor);
        Scene scene = arSceneView.getScene();
        anchorNode.setParent(scene);

        Color plankColor = new Color(0xFF593C1F);  // Brown RGB: 89, 60, 31
        MaterialFactory.makeOpaqueWithColor(this, plankColor)
                .thenAccept(material -> {
            if (simulationScenario == SimulationScenario.PlankTower) {
                buildTower(material, anchorNode);
            } else {
                buildPlankMatrix(material, anchorNode);
            }
            appState = AppState.TOWER_PLACED;
        });
    }

    private void hurdleBall(Vector3 startPosition, Vector3 targetPosition, ArSceneView arSceneView, Anchor anchor) {
        AnchorNode anchorNode = new AnchorNode(anchor);
        Scene scene = arSceneView.getScene();
        anchorNode.setParent(scene);

        Color ballColor = new Color(android.graphics.Color.RED);
        MaterialFactory.makeOpaqueWithColor(this, ballColor)
            .thenAccept(material -> {
                ModelRenderable renderable = ShapeFactory.makeSphere(
                    RADIUS,
                    new Vector3(0, 0, 0),
                    material
                );

                Node node = new Node();
                node.setParent(anchorNode);
                node.setRenderable(renderable);
                node.setLocalPosition(startPosition);

                // The camera look direction is the hurdle inertia, maybe scaling needed
                Vector3f velocityVector = new Vector3f(
                    targetPosition.x - startPosition.x,
                    targetPosition.y - startPosition.y,
                    targetPosition.z - startPosition.z
                );
                jBulletController.addBallRigidBody(
                    node,
                    new Vector3f(startPosition.x, startPosition.y, startPosition.z),
                    velocityVector
                );
                appState = AppState.BALL_HURDLED;
            });
    }

    @Override
    public void onTransformChanged(Node node, Node originatingNode) {
        if (cylinderNode != null && jBulletController != null && node == cylinderNode) {
            Vector3 position = node.getLocalPosition();
            jBulletController.updateCylinderLocation(
                new Vector3f(position.x, position.y, position.z));
        }
    }

    private void addCollisionBoxAndCylinder(ArSceneView arSceneView, Anchor anchor) {
        AnchorNode anchorNode = new AnchorNode(anchor);
        Scene scene = arSceneView.getScene();
        anchorNode.setParent(scene);

        Color ballColor = new Color(android.graphics.Color.RED);
        MaterialFactory.makeOpaqueWithColor(this, ballColor)
            .thenAccept(material -> {
                Vector3 startPosition = new Vector3(0, 0, 0);
                ModelRenderable renderable = ShapeFactory.makeCylinder(
                    WIDTH,
                    WIDTH,
                    new Vector3(0, 0, 0),
                    material
                );

                cylinderNode = new TransformableNode(fragment.getTransformationSystem());
                cylinderNode.addTransformChangedListener(this);
                cylinderNode.getScaleController().setEnabled(false);
                cylinderNode.getRotationController().setEnabled(false);
                EnumSet<Plane.Type> allowedPlaneTypes =
                        EnumSet.of(Plane.Type.HORIZONTAL_UPWARD_FACING,
                                Plane.Type.HORIZONTAL_DOWNWARD_FACING);
                cylinderNode.getTranslationController().setAllowedPlaneTypes(allowedPlaneTypes);
                cylinderNode.setParent(anchorNode);
                cylinderNode.setRenderable(renderable);
                cylinderNode.setLocalPosition(startPosition);

                jBulletController.addCylinderKineticBody(
                    new Vector3f(startPosition.x, startPosition.y, startPosition.z)
                );
                appState = AppState.BALL_HURDLED;
            });
    }

    private void addObjects(boolean isHurdle, ImageView iconButton) {
        if (isHurdle && appState == AppState.INITIAL) {
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
        Frame frame = arSceneView.getArFrame();
        boolean found = false;
        if (frame != null) {
            Point pt = getScreenCenter();
            List<HitResult> hits = frame.hitTest(pt.x, pt.y);
            for (HitResult hit : hits) {
                Trackable trackable = hit.getTrackable();
                Pose hitPose = hit.getHitPose();
                if (isHurdle || trackable instanceof Plane &&
                    ((Plane) trackable).isPoseInPolygon(hitPose))
                {
                    Anchor hitAnchor = null;
                    try {
                        hitAnchor = hit.createAnchor();
                    }
                    catch (FatalException ex) {
                        Log.d(TAG, "Unexpected error while trying to create anchor for the structure");
                    }
                    if (hitAnchor != null) {
                        found = true;
                        if (isHurdle) {
                            float[] hitTranslation = hitPose.getTranslation();
                            Vector3 ourPosition = new Vector3(hitTranslation[0], hitTranslation[1], hitTranslation[2]);
                            hurdleBall(new Vector3(0, 0, 0), ourPosition, arSceneView, hitAnchor);
                            ImageView crossHairIcon = findViewById(R.id.cross_hair);
                            crossHairIcon.setVisibility(View.GONE);
                        } else {
                            jBulletController = new JBulletController(getModelParameters(), simulationScenario);
                            iconButton.setEnabled(false);
                            spawnStructure(arSceneView, hitAnchor);
                            if (simulationScenario == SimulationScenario.CollisionBox) {
                                Anchor boxAnchor = null;
                                try {
                                    boxAnchor = hit.createAnchor();
                                }
                                catch (FatalException ex) {
                                    Log.d(TAG, "Unexpected error while trying to create cylinder");
                                }
                                addCollisionBoxAndCylinder(arSceneView, boxAnchor);
                                ImageView crossHairIcon = findViewById(R.id.cross_hair);
                                crossHairIcon.setVisibility(View.GONE);
                            }
                            iconButton.setEnabled(true);
                        }
                    }
                }
                break;
            }
        }
        if (!found) {
            String text = getString(
                    isHurdle ? R.string.aim_at_the_tower : R.string.wait_until_locked_in
            );
            Snackbar.make(findViewById(android.R.id.content),
                    text, Snackbar.LENGTH_SHORT).show();
        }
    }

    private void clearScene(boolean silent) {
        if (appState == AppState.INITIAL) {
            if (!silent) {
                String text = getString(R.string.already_clean_scene);
                Snackbar.make(findViewById(android.R.id.content),
                        text, Snackbar.LENGTH_SHORT).show();
            }

            return;
        }

        appState = AppState.INITIAL;
        if (simulationScenario == SimulationScenario.CollisionBox) {
            cylinderNode.removeTransformChangedListener(this);
            cylinderNode = null;
        }
        jBulletController.clearScene();
        jBulletController = null;
        // Clear the SceneForm scene
        fragment.getArSceneView().getScene().callOnHierarchy(node -> {
            if (node instanceof Camera) {
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

        ImageView crossHairIcon = findViewById(R.id.cross_hair);
        crossHairIcon.setVisibility(View.VISIBLE);
    }

    private void displayHelp() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.InfoDialogStyle);
        int resId = simulationScenario == SimulationScenario.PlankTower ?
                R.string.how_to_play_tower : R.string.how_to_play_box;
        builder.setMessage(getString(resId))
                .setTitle(getString(R.string.quick_help))
                .setPositiveButton("OK", null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void initializeGallery() {
        ImageView restartIcon = findViewById(R.id.restartIcon);
        restartIcon.setOnClickListener(view -> clearScene(false));

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
        });

        ImageView pantheonIcon = findViewById(R.id.pantheonIcon);
        pantheonIcon.setOnClickListener(view -> addObjects(false, pantheonIcon));

        ImageView aimIcon = findViewById(R.id.aimIcon);
        aimIcon.setOnClickListener(view -> {
            if (simulationScenario == SimulationScenario.PlankTower) {
                addObjects(true, null);
            } else {
                String text = getString(appState == AppState.INITIAL ?
                        R.string.box_before_yanking : R.string.move_the_cylinder);
                Snackbar.make(findViewById(android.R.id.content),
                        text, Snackbar.LENGTH_SHORT).show();
            }
        });

        ImageView step1Icon = findViewById(R.id.step1Icon);
        step1Icon.setOnClickListener(view -> displayHelp());

        ImageView step2Icon = findViewById(R.id.step2Icon);
        step2Icon.setOnClickListener(view -> displayHelp());
    }
}
