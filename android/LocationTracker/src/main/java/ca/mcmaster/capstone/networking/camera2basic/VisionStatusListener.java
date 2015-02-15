package ca.mcmaster.capstone.networking.camera2basic;

public interface VisionStatusListener {
    void onCircleFound(Circle circle);
    void onCircleLost(Circle circle);
}
