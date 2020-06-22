package dev.csaba.arphysics;

import android.content.Context;
import android.util.Log;

import com.bulletphysics.collision.broadphase.DbvtBroadphase;
import com.bulletphysics.collision.dispatch.CollisionDispatcher;
import com.bulletphysics.collision.dispatch.CollisionObject;
import com.bulletphysics.collision.dispatch.DefaultCollisionConfiguration;
import com.bulletphysics.collision.shapes.BoxShape;
import com.bulletphysics.collision.shapes.CollisionShape;
import com.bulletphysics.collision.shapes.IndexedMesh;
import com.bulletphysics.collision.shapes.SphereShape;
import com.bulletphysics.collision.shapes.StaticPlaneShape;
import com.bulletphysics.dynamics.DiscreteDynamicsWorld;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.RigidBodyConstructionInfo;
import com.bulletphysics.dynamics.constraintsolver.SequentialImpulseConstraintSolver;
import com.bulletphysics.linearmath.DefaultMotionState;
import com.bulletphysics.linearmath.Transform;
import com.bulletphysics.util.ObjectArrayList;
import com.google.ar.core.Pose;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;

import javax.vecmath.Quat4f;
import javax.vecmath.Vector3f;

import static com.bulletphysics.collision.dispatch.CollisionObject.DISABLE_DEACTIVATION;


public class PhysicsController {

  private static final String TAG = "PhysicsController";

  private ModelParameters modelParameters;
  private DiscreteDynamicsWorld dynamicsWorld;
  private SequentialImpulseConstraintSolver solver;
  private RigidBody ballRB;
  private Node ballNode;
  private RigidBody[] slabRBs;
  private Node[] slabNodes;
  private long previous_time;
  private Context context;
  private final float MAZE_SCALE = 0.02f;
  private final float MAZE_SCALE_Y_EXTRA = 0.1f;

  public PhysicsController(Context activity, ModelParameters modelParameters) {
    context = activity;
    this.modelParameters = modelParameters;
    initialize();
  }

  public void initialize() {
    // Initialize Bullet Physics Engine
    DefaultCollisionConfiguration collisionConfiguration = new DefaultCollisionConfiguration();
    CollisionDispatcher dispatcher = new CollisionDispatcher(collisionConfiguration);
    DbvtBroadphase broadPhase = new DbvtBroadphase();
    solver = new SequentialImpulseConstraintSolver();

    dynamicsWorld = new DiscreteDynamicsWorld(dispatcher, broadPhase, solver, collisionConfiguration);

    // Override default gravity (which would be (0, -10, 0)) with configured one
    dynamicsWorld.setGravity(new Vector3f(0f, -modelParameters.getGravity(), 0f));

    addGroundPlane();

    slabRBs = new RigidBody[modelParameters.getNumFloors() * 2];
    slabNodes = new Node[modelParameters.getNumFloors() * 2];
    previous_time = java.lang.System.currentTimeMillis();
  }

  public void addBallRigidBody(Node ballNode, Vector3f ballPosition, Vector3f inertia) {
    this.ballNode = ballNode;
    float r = modelParameters.getRadius();
    CollisionShape ballShape = new SphereShape(r);

    Transform ballTransform = new Transform();
    ballTransform.setIdentity();
    ballTransform.origin.set(ballPosition); // Slightly raise the ball at the beginning

    DefaultMotionState ballMotionState = new DefaultMotionState(ballTransform);
    float mass = (float)(modelParameters.getBallDensity() * 1000 * 4 / 3 * Math.PI * r * r * r);
    RigidBodyConstructionInfo ballRBInfo = new RigidBodyConstructionInfo(
        mass, ballMotionState, ballShape, inertia);
    ballRBInfo.restitution = modelParameters.getBallRestitution();
    ballRBInfo.friction = modelParameters.getBallFriction();

    ballRB = new RigidBody(ballRBInfo);
    ballRB.setActivationState(DISABLE_DEACTIVATION);

    dynamicsWorld.addRigidBody(ballRB);
  }

  public void addGroundPlane() {
    CollisionShape groundShape = new StaticPlaneShape(
      new Vector3f(0, 1.0f, 0),
        0);

    Transform groundTransform = new Transform();
    groundTransform.setIdentity();
    groundTransform.origin.set(0, 0, 0);

    DefaultMotionState groundMotionState = new DefaultMotionState(groundTransform);
    RigidBodyConstructionInfo groundRBInfo = new RigidBodyConstructionInfo(
            0.0f, groundMotionState, groundShape, new Vector3f(0, 0, 0));
    groundRBInfo.friction = 0.6f;
    RigidBody groundRB = new RigidBody(groundRBInfo);
    dynamicsWorld.addRigidBody(groundRB);
  }

  public void addSlabRigidBody(int index, Node slabNode, Vector3f slabBox, Vector3f slabPosition) {
    this.slabNodes[index] = slabNode;
    float r = modelParameters.getRadius();
    CollisionShape slabShape = new BoxShape(slabBox);

    Transform slabTransform = new Transform();
    slabTransform.setIdentity();
    slabTransform.origin.set(slabPosition); // Slightly raise the ball at the beginning

    DefaultMotionState slabMotionState = new DefaultMotionState(slabTransform);
    float mass = modelParameters.getSlabDensity() * 1000 * slabBox.x * slabBox.y * slabBox.z;
    RigidBodyConstructionInfo slabRBInfo = new RigidBodyConstructionInfo(
            mass, slabMotionState, slabShape, new Vector3f(0, 0, 0));
    slabRBInfo.restitution = modelParameters.getBallRestitution();
    slabRBInfo.friction = modelParameters.getBallFriction();

    RigidBody slabRB = new RigidBody(slabRBInfo);
    slabRB.setActivationState(DISABLE_DEACTIVATION);
    slabRBs[index] = slabRB;

    dynamicsWorld.addRigidBody(slabRB);
  }

  public void updatePhysics() {
    long current_time = java.lang.System.currentTimeMillis();

    // stepSimulation takes deltaTime in the unit of seconds
    dynamicsWorld.stepSimulation((current_time - previous_time) / 1000.0f);
    previous_time = current_time;

    // printDebugInfo();
  }

  private void printDebugInfo() {
    //
    // Help print out debug info
    //
    int numObj = dynamicsWorld.getNumCollisionObjects();
    ObjectArrayList<CollisionObject> objArray = dynamicsWorld.getCollisionObjectArray();
    for (int j = 0; j < numObj; ++j) {
      CollisionObject collisionObj = objArray.get(j);
      RigidBody body = RigidBody.upcast(collisionObj);
      Transform worldTransform = new Transform();
      int state = collisionObj.getActivationState();
      if (body != null && body.getMotionState() != null) {
        body.getMotionState().getWorldTransform(worldTransform);
      } else {
        collisionObj.getWorldTransform(worldTransform);
      }

      Log.d(TAG,
          String.format("obj %d status [%d] World transform %f, %f, %f",
              j, state,
              worldTransform.origin.x, worldTransform.origin.y, worldTransform.origin.z));
    }
  }

  // Get the pose on Ball, in Maze's coordinate space
  // - With centered the vertices in Maze, (0, 0, 0) is the center of the maze
  // - Though we scaled maze in physics simulation, the Pose returned here is not scaled.
  public Pose getBallPose() {
    Transform ballTransform = new Transform();
    ballRB.getMotionState().getWorldTransform(ballTransform);

    Quat4f rot = new Quat4f();
    ballTransform.getRotation(rot);

    // Use MAZE_SCALE to convert size from physical world size to Maze's original size
    // Because in display size, Sceneform is actually dealing with original size of Maze
    float translation[] = {ballTransform.origin.x / MAZE_SCALE, ballTransform.origin.y / MAZE_SCALE, ballTransform.origin.z/ MAZE_SCALE};
    float rotation[] = {rot.x, rot.y, rot.z, rot.w};

    Pose ballPose = new Pose(translation, rotation);
    return ballPose;
  }

  public void applyGravityToBall(Vector3f gravity) {
    ballRB.applyCentralForce(gravity);
  }
}
