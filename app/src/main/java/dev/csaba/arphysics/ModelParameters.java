package dev.csaba.arphysics;

public class ModelParameters {
    private float gravity;
    private float slabRestitution;
    private float slabFriction;
    private float slabDensity;
    private float ballRestitution;
    private float ballFriction;
    private float ballDensity;

    public ModelParameters(float gravity, float slabRestitution, float slabFriction,
                           float slabDensity, float ballRestitution, float ballFriction,
                           float ballDensity)
    {
        this.gravity = gravity;
        this.slabRestitution = slabRestitution;
        this.slabFriction = slabFriction;
        this.slabDensity = slabDensity;
        this.ballRestitution = ballRestitution;
        this.ballFriction = ballFriction;
        this.ballDensity = ballDensity;
    }

    public float getGravity() {
        return gravity;
    }

    public void setGravity(float gravity) {
        this.gravity = gravity;
    }

    public float getSlabRestitution() {
        return slabRestitution;
    }

    public void setSlabRestitution(float slabRestitution) {
        this.slabRestitution = slabRestitution;
    }

    public float getSlabFriction() {
        return slabFriction;
    }

    public void setSlabFriction(float slabFriction) {
        this.slabFriction = slabFriction;
    }

    public float getSlabDensity() {
        return slabDensity;
    }

    public void setSlabDensity(float slabDensity) {
        this.slabDensity = slabDensity;
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
}
