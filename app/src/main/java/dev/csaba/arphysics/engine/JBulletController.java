package dev.csaba.arphysics.engine;

import android.util.Log;

import com.bulletphysics.collision.broadphase.DbvtBroadphase;
import com.bulletphysics.collision.dispatch.CollisionDispatcher;
import com.bulletphysics.collision.dispatch.CollisionFlags;
import com.bulletphysics.collision.dispatch.CollisionObject;
import com.bulletphysics.collision.dispatch.DefaultCollisionConfiguration;
import com.bulletphysics.collision.shapes.BoxShape;
import com.bulletphysics.collision.shapes.CollisionShape;
import com.bulletphysics.collision.shapes.CylinderShape;
import com.bulletphysics.collision.shapes.SphereShape;
import com.bulletphysics.collision.shapes.StaticPlaneShape;
import com.bulletphysics.dynamics.DiscreteDynamicsWorld;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.RigidBodyConstructionInfo;
import com.bulletphysics.dynamics.constraintsolver.SequentialImpulseConstraintSolver;
import com.bulletphysics.linearmath.DefaultMotionState;
import com.bulletphysics.linearmath.MotionState;
import com.bulletphysics.linearmath.Transform;
import com.bulletphysics.util.ObjectArrayList;
import com.google.ar.core.Pose;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;

import javax.vecmath.Quat4f;
import javax.vecmath.Vector3f;

import dev.csaba.arphysics.ModelParameters;
import dev.csaba.arphysics.SimulationScenario;


public class JBulletController {

  private static final String TAG = "JBulletController";

  private ModelParameters modelParameters;
  private DiscreteDynamicsWorld dynamicsWorld;
  private RigidBody ballRB;
  private Node ballNode;
  private RigidBody cylinderRB;
  private RigidBody[] plankRBs;
  private Node[] plankNodes;
  private long previousTime;
  private int slowMotion;
  private Vector3f zeroVector;
  private SimulationScenario simulationScenario;
  private int plankCount;

  public JBulletController(ModelParameters modelParameters, SimulationScenario simulationScenario) {
    this.modelParameters = modelParameters;
    this.slowMotion = modelParameters.getSlowMotion();
    this.simulationScenario = simulationScenario;
    initialize();
  }

  public void initialize() {
    // Initialize Bullet Physics Engine
    DefaultCollisionConfiguration collisionConfiguration = new DefaultCollisionConfiguration();
    CollisionDispatcher dispatcher = new CollisionDispatcher(collisionConfiguration);
    DbvtBroadphase broadPhase = new DbvtBroadphase();
    SequentialImpulseConstraintSolver solver = new SequentialImpulseConstraintSolver();

    dynamicsWorld = new DiscreteDynamicsWorld(dispatcher, broadPhase, solver, collisionConfiguration);

    // Override default gravity (which would be (0, -10, 0)) with configured one
    dynamicsWorld.setGravity(new Vector3f(0f, -modelParameters.getGravity(), 0f));

    zeroVector = new Vector3f(0, 0, 0);

    addGroundPlane();

    int plankCountMultiplier = simulationScenario == SimulationScenario.PlankTower ? 2 :
            modelParameters.getNumFloors();
    plankCount = modelParameters.getNumFloors() * plankCountMultiplier;
    plankRBs = new RigidBody[plankCount];
    plankNodes = new Node[plankCount];
  }

  public void addBallRigidBody(Node ballNode, Vector3f ballPosition, Vector3f velocity) {
    this.ballNode = ballNode;
    float r = modelParameters.getRadius();
    CollisionShape ballShape = new SphereShape(r);

    Transform ballTransform = new Transform();
    ballTransform.setIdentity();
    ballTransform.origin.set(ballPosition);

    DefaultMotionState ballMotionState = new DefaultMotionState(ballTransform);
    float mass = (float)(modelParameters.getBallDensity() * 4 / 3 * Math.PI * r * r * r);
    ballShape.calculateLocalInertia(mass, zeroVector);
    RigidBodyConstructionInfo ballRBInfo = new RigidBodyConstructionInfo(
        mass, ballMotionState, ballShape, zeroVector);
    ballRBInfo.restitution = modelParameters.getBallRestitution();
    ballRBInfo.friction = modelParameters.getBallFriction();

    ballRB = new RigidBody(ballRBInfo);
    // ballRB.setActivationState(DISABLE_DEACTIVATION);
    // ballRB.setDeactivationTime(5f);
    ballRB.setLinearVelocity(velocity);
    ballRB.setSleepingThresholds(0.8f, 1.0f);
    dynamicsWorld.addRigidBody(ballRB);
    previousTime = java.lang.System.currentTimeMillis();
  }

  private void addCollisionBoxWall(Vector3f normal, Vector3f position) {
    CollisionShape wallShape = new StaticPlaneShape(
            new Vector3f(normal.x, normal.y, normal.z), 0);
    wallShape.setMargin(modelParameters.getConvexMargin());

    Transform wallTransform = new Transform();
    wallTransform.setIdentity();
    wallTransform.origin.set(position.x, position.y, position.z);

    DefaultMotionState wallMotionState = new DefaultMotionState(wallTransform);
    RigidBodyConstructionInfo wallRBInfo = new RigidBodyConstructionInfo(
            0.0f, wallMotionState, wallShape, zeroVector);
    wallRBInfo.friction = 0.6f;
    RigidBody wallRB = new RigidBody(wallRBInfo);
    dynamicsWorld.addRigidBody(wallRB);
  }

  public void addCylinderKineticBody(Vector3f cylinderPosition) {
    float r = modelParameters.getWidth();
    CollisionShape cylinderShape = new CylinderShape(new Vector3f(r, r, r));

    Transform cylinderTransform = new Transform();
    cylinderTransform.setIdentity();
    cylinderTransform.origin.set(cylinderPosition);

    DefaultMotionState cylinderMotionState = new DefaultMotionState(cylinderTransform);
    // https://pybullet.org/Bullet/phpBB3/viewtopic.php?t=7086
    // Kinematic Object's mass is 0.0
    RigidBodyConstructionInfo cylinderRBInfo = new RigidBodyConstructionInfo(
        0, cylinderMotionState, cylinderShape, zeroVector);
    cylinderRBInfo.restitution = modelParameters.getBallRestitution();
    cylinderRBInfo.friction = modelParameters.getBallFriction();

    cylinderRB = new RigidBody(cylinderRBInfo);
    cylinderRB.setCollisionFlags(CollisionFlags.KINEMATIC_OBJECT);
    cylinderRB.setActivationState(CollisionObject.DISABLE_DEACTIVATION);
    dynamicsWorld.addRigidBody(cylinderRB);

    addCollisionBoxWall(new Vector3f(0.5f, 0, 0), new Vector3f(-0.5f, 0, 0));
    addCollisionBoxWall(new Vector3f(0, 0, 0.5f), new Vector3f(0, 0, -0.5f));
    addCollisionBoxWall(new Vector3f(-0.5f, 0, 0), new Vector3f(0.5f, 0, 0));
    addCollisionBoxWall(new Vector3f(0, 0, -0.5f), new Vector3f(0, 0, 0.5f));
    // previousTime = java.lang.System.currentTimeMillis();
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
        0.0f, groundMotionState, groundShape, zeroVector);
    groundRBInfo.friction = 0.6f;
    RigidBody groundRB = new RigidBody(groundRBInfo);
    dynamicsWorld.addRigidBody(groundRB);
  }

  public void addPlankRigidBody(int index, Node plankNode, Vector3f plankBox, Vector3f plankPosition) {
    this.plankNodes[index] = plankNode;
    float margin = modelParameters.getConvexMargin();
    float marginShrink = 0.0f;  // margin;
    float doubleMargin = marginShrink * 2;
    // We need to shrink the box with the margin, so
    // the planks would touch and would not float on each other.
    // This has to be reversed in updatePhysics.
    Vector3f compensatedPlankBox = new Vector3f(
      plankBox.x - doubleMargin,
      plankBox.y - doubleMargin,
      plankBox.z - doubleMargin
    );
    CollisionShape plankShape = new BoxShape(compensatedPlankBox);
    plankShape.setMargin(margin);

    Transform plankTransform = new Transform();
    plankTransform.setIdentity();
    // We need to compensate the position due to the PlankBox shrink.
    // This has to be reversed in updatePhysics.
    Vector3f compensatedPlankPosition = new Vector3f(
      plankPosition.x + marginShrink,
      plankPosition.y + marginShrink,
      plankPosition.z + marginShrink
    );
    plankTransform.origin.set(compensatedPlankPosition);

    DefaultMotionState plankMotionState = new DefaultMotionState(plankTransform);
    float mass = modelParameters.getPlankDensity() * plankBox.x * plankBox.y * plankBox.z;
    plankShape.calculateLocalInertia(mass, zeroVector);
    RigidBodyConstructionInfo plankRBInfo = new RigidBodyConstructionInfo(
        mass, plankMotionState, plankShape, zeroVector);
    plankRBInfo.restitution = modelParameters.getBallRestitution();
    plankRBInfo.friction = modelParameters.getBallFriction();

    RigidBody plankRB = new RigidBody(plankRBInfo);
    // plankRB.setActivationState(DISABLE_DEACTIVATION);
    plankRB.setSleepingThresholds(0.8f, 1.0f);
    plankRBs[index] = plankRB;

    dynamicsWorld.addRigidBody(plankRB);

    /*
    if (index == plankCount - 1) {
      previousTime = java.lang.System.currentTimeMillis();
    }
    */
  }

  public Pose getElementPose(RigidBody rigidBody) {
    Transform elementTransform = new Transform();
    rigidBody.getMotionState().getWorldTransform(elementTransform);

    Quat4f rot = new Quat4f();
    elementTransform.getRotation(rot);

    float margin = 0.0f;  // modelParameters.getConvexMargin();
    // Reverse the margin compensation.
    // A rudimentary version doesn't account for the rotation,
    // so it could miscalculate by sqrt(2)-1 (0.414213562) x margin.
    // Since the margin is already small this might not be much visible.
    // Otherwise we'll have to calculate the proper geometry based on the rotation.
    float[] translation = {
      elementTransform.origin.x - margin,
      elementTransform.origin.y - margin,
      elementTransform.origin.z - margin
    };
    float[] rotation = {
      rot.x,
      rot.y,
      rot.z,
      rot.w
    };

    return new Pose(translation, rotation);
  }

  public void updateCylinderLocation(Vector3f cylinderPosition) {
    if (cylinderRB == null) {
      return;
    }
    if (previousTime <= 0) {
      // Start the simulation as soon as the cylinder moves the first time
      previousTime = java.lang.System.currentTimeMillis();
    }

    Transform elementTransform = new Transform();
    MotionState motionState = cylinderRB.getMotionState();
    motionState.getWorldTransform(elementTransform);
    Vector3f translation = new Vector3f(
      cylinderPosition.x - elementTransform.origin.x,
      cylinderPosition.y - elementTransform.origin.y,
      cylinderPosition.z - elementTransform.origin.z
    );
    if (Math.abs(translation.x) < 1e-6 && Math.abs(translation.y) < 1e-6 && Math.abs(translation.z) < 1e-6) {
      return;
    }

    Transform cylinderTransform = new Transform();
    cylinderTransform.setIdentity();
    cylinderTransform.origin.set(cylinderPosition);
    DefaultMotionState cylinderMotionState = new DefaultMotionState(cylinderTransform);

    cylinderRB.setWorldTransform(cylinderTransform);
    cylinderRB.setMotionState(cylinderMotionState);
    cylinderRB.setLinearVelocity(new Vector3f(0, 0, 0));
    cylinderRB.setAngularVelocity(new Vector3f(0, 0, 0));
    cylinderRB.clearForces();
  }

  public void updatePhysics() {
    // Approximately called with 30 FPS in my tests
    if (previousTime <= 0) {
      return;
    }
    long currentTime = java.lang.System.currentTimeMillis();
    long timeDeltaMillis = currentTime - previousTime;
    if (timeDeltaMillis <= 0) {
      return;
    }

    if (slowMotion > 1) {
      timeDeltaMillis /= slowMotion;
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

    // Update the planks
    int plankCount = plankRBs.length;
    for (int index = 0; index < plankCount; index++) {
      Node plankNode = plankNodes[index];
      if (plankNode != null) {
        Pose pose = getElementPose(plankRBs[index]);
        plankNode.setLocalPosition(new Vector3(pose.tx(), pose.ty(), pose.tz()));
        plankNode.setLocalRotation(new Quaternion(pose.qx(), pose.qy(), pose.qz(), pose.qw()));
      }
    }

    // printDebugInfo();
  }

  public void clearScene() {
    ballNode = null;
    if (ballRB != null) {
      dynamicsWorld.removeRigidBody(ballRB);
    }
    if (cylinderRB != null) {
      dynamicsWorld.removeRigidBody(cylinderRB);
    }

    int plankCount = plankRBs.length;
    for (int index = 0; index < plankCount; index++) {
      plankNodes[index] = null;
      dynamicsWorld.removeRigidBody(plankRBs[index]);
    }
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
