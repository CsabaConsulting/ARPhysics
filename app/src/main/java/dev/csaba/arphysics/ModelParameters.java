package dev.csaba.arphysics;

public class ModelParameters {
    private int numFloors;
    private float gravity;
    private float plankRestitution;
    private float plankFriction;
    private float plankDensity;
    private float ballRestitution;
    private float ballFriction;
    private float ballDensity;
    private float width;
    private float height;
    private float depth;
    private float radius;
    private float convexMargin;
    private int slowMotion;

    public ModelParameters(int numFloors, float gravity, float plankRestitution, float plankFriction,
                           float plankDensity, float ballRestitution, float ballFriction,
                           float ballDensity, float width, float height, float depth, float radius,
                           float convexMargin, int slowMotion)
    {
        this.numFloors = numFloors;
        this.gravity = gravity;
        this.plankRestitution = plankRestitution;
        this.plankFriction = plankFriction;
        this.plankDensity = plankDensity;
        this.ballRestitution = ballRestitution;
        this.ballFriction = ballFriction;
        this.ballDensity = ballDensity;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.radius = radius;
        this.convexMargin = convexMargin;
        this.slowMotion = slowMotion;
    }

    public int getNumFloors() {
        return numFloors;
    }

    public void setNumFloors(int numFloors) {
        this.numFloors = numFloors;
    }

    public float getGravity() {
        return gravity;
    }

    public void setGravity(float gravity) {
        this.gravity = gravity;
    }

    public float getPlankRestitution() {
        return plankRestitution;
    }

    public void setPlankRestitution(float plankRestitution) {
        this.plankRestitution = plankRestitution;
    }

    public float getPlankFriction() {
        return plankFriction;
    }

    public void setPlankFriction(float plankFriction) {
        this.plankFriction = plankFriction;
    }

    public float getPlankDensity() {
        return plankDensity;
    }

    public void setPlankDensity(float plankDensity) {
        this.plankDensity = plankDensity;
    }

    public float getBallRestitution() {
        return ballRestitution;
    }

    public void setBallRestitution(float ballRestitution) {
        this.ballRestitution = ballRestitution;
    }

    public float getBallFriction() {
        return ballFriction;
    }

    public void setBallFriction(float ballFriction) {
        this.ballFriction = ballFriction;
    }

    public float getBallDensity() {
        return ballDensity;
    }

    public void setBallDensity(float ballDensity) {
        this.ballDensity = ballDensity;
    }

    public float getWidth() {
        return width;
    }

    public void setWidth(float width) {
        this.width = width;
    }

    public float getHeight() {
        return height;
    }

    public void setHeight(float height) {
        this.height = height;
    }

    public float getDepth() {
        return depth;
    }

    public void setDepth(float depth) {
        this.depth = depth;
    }

    public float getRadius() {
        return radius;
    }

    public void setRadius(float radius) {
        this.radius = radius;
    }

    public float getConvexMargin() {
        return convexMargin;
    }

    public void setConvexMargin(float convexMargin) {
        this.convexMargin = convexMargin;
    }

    public int getSlowMotion() {
        return slowMotion;
    }

    public void setSlowMotion(int slowMotion) {
        this.slowMotion = slowMotion;
    }
}
