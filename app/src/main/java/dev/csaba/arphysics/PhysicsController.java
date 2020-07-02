package dev.csaba.arphysics;

import android.util.Log;

import com.bulletphysics.collision.broadphase.DbvtBroadphase;
import com.bulletphysics.collision.dispatch.CollisionDispatcher;
import com.bulletphysics.collision.dispatch.CollisionFlags;
import com.bulletphysics.collision.dispatch.CollisionObject;
import com.bulletphysics.collision.dispatch.DefaultCollisionConfiguration;
import com.bulletphysics.collision.shapes.BoxShape;
import com.bulletphysics.collision.shapes.CollisionShape;
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
  private long previousTime;

  public PhysicsController(ModelParameters modelParameters) {
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
  }

  public void addBallRigidBody(Node ballNode, Vector3f ballPosition, Vector3f inertia) {
    Log.d(TAG,
            String.format("Ball pos %f, %f, %f, inertia %f, %f, %f",
                    ballPosition.x, ballPosition.y, ballPosition.z,
                    inertia.x, inertia.y, inertia.z)
    );

    this.ballNode = ballNode;
    float r = modelParameters.getRadius();
    CollisionShape ballShape = new SphereShape(r);

    Transform ballTransform = new Transform();
    ballTransform.setIdentity();
    ballTransform.origin.set(ballPosition);

    DefaultMotionState ballMotionState = new DefaultMotionState(ballTransform);
    float mass = (float)(modelParameters.getBallDensity() * 4 / 3 * Math.PI * r * r * r);
    RigidBodyConstructionInfo ballRBInfo = new RigidBodyConstructionInfo(
        mass, ballMotionState, ballShape, inertia);
    ballRBInfo.restitution = modelParameters.getBallRestitution();
    ballRBInfo.friction = modelParameters.getBallFriction();

    ballRB = new RigidBody(ballRBInfo);
    ballRB.setActivationState(DISABLE_DEACTIVATION);
    dynamicsWorld.addRigidBody(ballRB);
    previousTime = java.lang.System.currentTimeMillis();
  }

  public void addGroundPlane() {
    CollisionShape groundShape = new StaticPlaneShape(
      new Vector3f(0, 1.0f, 0), 0);
    groundShape.setMargin(modelParameters.getConvexMargin());

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
    float margin = modelParameters.getConvexMargin();
    float doubleMargin = margin * 2;
    // We need to shrink the box with the margin, so
    // the slabs would touch and would not float on each other.
    // This has to be reversed in updatePhysics.
    Vector3f compensatedSlabBox = new Vector3f(
      slabBox.x - doubleMargin,
      slabBox.y - doubleMargin,
      slabBox.z - doubleMargin
    );
    CollisionShape slabShape = new BoxShape(compensatedSlabBox);
    slabShape.setMargin(margin);

    Transform slabTransform = new Transform();
    slabTransform.setIdentity();
    // We need to compensate the position due to the SlabBox shrink.
    // This has to be reversed in updatePhysics.
    Vector3f compensatedSlabPosition = new Vector3f(
      slabPosition.x + margin,
      slabPosition.y + margin,
      slabPosition.z + margin
    );
    slabTransform.origin.set(compensatedSlabPosition);

    DefaultMotionState slabMotionState = new DefaultMotionState(slabTransform);
    float mass = modelParameters.getSlabDensity() * slabBox.x * slabBox.y * slabBox.z;
    RigidBodyConstructionInfo slabRBInfo = new RigidBodyConstructionInfo(
        mass, slabMotionState, slabShape, new Vector3f(0, 0, 0));
    slabRBInfo.restitution = modelParameters.getBallRestitution();
    slabRBInfo.friction = modelParameters.getBallFriction();

    RigidBody slabRB = new RigidBody(slabRBInfo);
    slabRB.setActivationState(DISABLE_DEACTIVATION);
    slabRBs[index] = slabRB;

    dynamicsWorld.addRigidBody(slabRB);
  }

  public Pose getElementPose(RigidBody rigidBody) {
    Transform ballTransform = new Transform();
    rigidBody.getMotionState().getWorldTransform(ballTransform);

    Quat4f rot = new Quat4f();
    ballTransform.getRotation(rot);

    float margin = modelParameters.getConvexMargin();
    // Reverse the margin compensation.
    // A rudimentary version doesn't account for the rotation,
    // so it could miscalculate by sqrt(2)-1 (0.414213562) x margin.
    // Since the margin is already small this might not be much visible.
    // Otherwise we'll have to calculate the proper geometry based on the rotation.
    float[] translation = {
      ballTransform.origin.x - margin,
      ballTransform.origin.y - margin,
      ballTransform.origin.z - margin
    };
    float[] rotation = {
      rot.x,
      rot.y,
      rot.z,
      rot.w
    };

    return new Pose(translation, rotation);
  }

  public void updatePhysics() {
    if (previousTime <= 0) {
      return;
    }
    long currentTime = java.lang.System.currentTimeMillis();
    long timeDeltaMillis = currentTime - previousTime;
    if (timeDeltaMillis <= 0) {
      return;
    }

    // stepSimulation takes deltaTime in the unit of seconds
    dynamicsWorld.stepSimulation(timeDeltaMillis / 1000.0f);
    previousTime = currentTime;

    // Update the ball
    if (ballNode != null) {
      Pose pose = getElementPose(ballRB);
      ballNode.setLocalPosition(new Vector3(pose.tx(), pose.ty(), pose.tz()));
      ballNode.setLocalRotation(new Quaternion(pose.qx(), pose.qy(), pose.qz(), pose.qw()));
    }

    // Update the slabs
    int slabCount = slabRBs.length;
    for (int index = 0; index < slabCount; index++) {
      Node slabNode = slabNodes[index];
      if (slabNode != null) {
        Pose pose = getElementPose(slabRBs[index]);
        slabNode.setLocalPosition(new Vector3(pose.tx(), pose.ty(), pose.tz()));
        slabNode.setLocalRotation(new Quaternion(pose.qx(), pose.qy(), pose.qz(), pose.qw()));
      }
    }

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
}
