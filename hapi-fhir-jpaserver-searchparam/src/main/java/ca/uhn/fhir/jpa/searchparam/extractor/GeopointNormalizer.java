package ca.uhn.fhir.jpa.searchparam.extractor;

/**
 * TODO GGG HS: This class fully and unabashedly stolen from Hibernate search 5.11.4 FINAL's implementation.
 */
public class GeopointNormalizer {

	static int WHOLE_CIRCLE_DEGREE_RANGE = 360;
	static int LONGITUDE_DEGREE_RANGE = WHOLE_CIRCLE_DEGREE_RANGE;
	static int LATITUDE_DEGREE_RANGE = WHOLE_CIRCLE_DEGREE_RANGE / 2;
	static int LATITUDE_DEGREE_MIN = -LATITUDE_DEGREE_RANGE / 2;
	static int LATITUDE_DEGREE_MAX = LATITUDE_DEGREE_RANGE / 2;

	public static double normalizeLongitude(double longitude) {
		if ( longitude == ( -LONGITUDE_DEGREE_RANGE / 2 ) ) {
			return LONGITUDE_DEGREE_RANGE / 2 ;
		}
		else {
			return normalizeLongitudeInclusive( longitude );
		}
	}

	public static double normalizeLongitudeInclusive(double longitude) {
		if ( (longitude < -( LONGITUDE_DEGREE_RANGE / 2 ) ) || (longitude > ( LONGITUDE_DEGREE_RANGE / 2 ) ) ) {
			double _longitude;
			// shift 180 and normalize full circle turn
			_longitude = ( ( longitude + ( LONGITUDE_DEGREE_RANGE / 2 ) ) % WHOLE_CIRCLE_DEGREE_RANGE );
			// as Java % is not a math modulus we may have negative numbers so the unshift is sign dependant
			if ( _longitude < 0 ) {
				_longitude = _longitude + ( LONGITUDE_DEGREE_RANGE / 2 );
			}
			else {
				_longitude = _longitude - ( LONGITUDE_DEGREE_RANGE / 2 );
			}
			return _longitude;
		}
		else {
			return longitude;
		}
	}

	/**
	 * @param latitude in degrees
	 * @return latitude normalized in [-90;+90]
	 */
	public static double normalizeLatitude(double latitude) {
		if ( latitude > LATITUDE_DEGREE_MAX || latitude < LATITUDE_DEGREE_MIN ) {
			// shift 90, normalize full circle turn and 'symmetry' on the lat axis with abs
			double _latitude = Math.abs( ( latitude + ( LATITUDE_DEGREE_RANGE / 2 ) ) % ( WHOLE_CIRCLE_DEGREE_RANGE ) );
			// Push 2nd and 3rd quadran in 1st and 4th by 'symmetry'
			if ( _latitude > LATITUDE_DEGREE_RANGE ) {
				_latitude = WHOLE_CIRCLE_DEGREE_RANGE - _latitude;
			}
			// unshift
			_latitude = _latitude - ( LATITUDE_DEGREE_RANGE / 2 );
			return _latitude;
		}
		else {
			return latitude;
		}
	}
}
