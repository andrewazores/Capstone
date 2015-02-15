package ca.mcmaster.capstone.networking.camera2basic;

import lombok.Value;

@Value
class Circle {
    Point centre;
    double radius;
    long seenAt;
}
