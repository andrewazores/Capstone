#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <dirent.h>
#include <unistd.h>
#include <errno.h>
#include <time.h>
#include <math.h>
#define RADIANS_TO_DEGREES(radians) ((radians) * (180.0 / M_PI)) and
#define DEGREES_TO_RADIANS(angle) ((angle) / 180.0 * M_PI)

double get_distance2(double p1_altitude, double p1_lattitude,
		double p1_longitude, double p2_altitude, double p2_lattitude,
		double p2_longitude) {

	//http://answers.google.com/answers/threadview/id/326655.html
	/*
	 x = alt * cos(lat) * sin(long)
	 y = alt * sin(lat)
	 z = alt * cos(lat) * cos(long)
	 dist = sqrt( (x1-x0)^2 + (y1-y0)^2 + (z1-z0)^2 )

	 http://www.movable-type.co.uk/scripts/latlong.html
	 var R = 6371; // km
	 var φ1 = lat1.toRadians();
	 var φ2 = lat2.toRadians();
	 var Δφ = (lat2-lat1).toRadians();
	 var Δλ = (lon2-lon1).toRadians();

	 var a = Math.sin(Δφ/2) * Math.sin(Δφ/2) +
	 Math.cos(φ1) * Math.cos(φ2) *
	 Math.sin(Δλ/2) * Math.sin(Δλ/2);
	 var c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));

	 var d = R * c;

	 */
	double x1 = p1_altitude * cos(p1_lattitude) * sin(p1_longitude);
	double y1 = p1_altitude * sin(p1_lattitude);
	double z1 = p1_altitude * cos(p1_lattitude) * cos(p1_longitude);

	double x2 = p2_altitude * cos(p2_lattitude) * sin(p2_longitude);
	double y2 = p2_altitude * sin(p2_lattitude);
	double z2 = p2_altitude * cos(p2_lattitude) * cos(p2_longitude);
	double distance = sqrt(
			pow((x1 - x2), 2) + pow((y1 - y2), 2) + pow((z1 - z2), 2));

	return distance;
}

double get_distance(double p1_altitude, double p1_lattitude,
		double p1_longitude, double p2_altitude, double p2_lattitude,
		double p2_longitude) {
	//http://www.movable-type.co.uk/scripts/latlong.html
	double R = 6371; // km
	double phi1 = DEGREES_TO_RADIANS(p1_lattitude);
	double phi2 = DEGREES_TO_RADIANS(p2_lattitude);
	double delta_phi = DEGREES_TO_RADIANS(p2_lattitude - p1_lattitude);
	double delta_lamda = DEGREES_TO_RADIANS(p2_longitude - p1_longitude);

	double a = sin(delta_phi / 2) * sin(delta_phi / 2)
			+ cos(phi1) * cos(phi2) * sin(delta_lamda / 2)
					* sin(delta_lamda / 2);
	double c = 2 * atan2(sqrt(a), sqrt(1 - a));

	double d = R * c;
	return d;
}
