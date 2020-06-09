package acs.boundaries.details;

public class Location {
	private Double lat;
	private Double lng;

	public Location() {
	}

	public Location(double lat, double lng) {
		this.lat = lat;
		this.lng = lng;
	}

	public double getLat() {
		return lat;
	}

	public void setLat(double lat) {
		this.lat = lat;
	}

	public double getLng() {
		return lng;
	}

	public void setLng(double lng) {
		this.lng = lng;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((lat == null) ? 0 : lat.hashCode());
		result = prime * result + ((lng == null) ? 0 : lng.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Location other = (Location) obj;
		if (this.lat.equals(other.getLat()) && this.lng.equals(other.getLng())) {
			return true;
		}
		return false;
	}

	@Override
	public String toString() {
		return "LocationBoundary [lat=" + lat + ", lng=" + lng + "]";
	}

}
