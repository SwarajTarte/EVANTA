// Event.java — full file, now Parcelable
package com.example.evanta;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.gson.annotations.SerializedName;

public class Event implements Parcelable {

    private String id;
    private String title;
    private String subtitle;
    private String description;
    private String category;
    private double price;
    private int capacity;

    @SerializedName("date_start")
    private String dateStart;

    @SerializedName("date_end")
    private String dateEnd;

    @SerializedName("time_start")
    private String timeStart;

    @SerializedName("registration_deadline")
    private String registrationDeadline;

    private String location;

    @SerializedName("college_id")
    private String collegeId;

    @SerializedName("image_url")
    private String imageUrl;

    @SerializedName("is_featured")
    private boolean isFeatured;

    public Event() {
    }

    protected Event(Parcel in) {
        id = in.readString();
        title = in.readString();
        subtitle = in.readString();
        description = in.readString();
        category = in.readString();
        price = in.readDouble();
        capacity = in.readInt();
        dateStart = in.readString();
        dateEnd = in.readString();
        timeStart = in.readString();
        registrationDeadline = in.readString();
        location = in.readString();
        imageUrl = in.readString();
        isFeatured = in.readByte() != 0;
        collegeId = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(title);
        dest.writeString(subtitle);
        dest.writeString(description);
        dest.writeString(category);
        dest.writeDouble(price);
        dest.writeInt(capacity);
        dest.writeString(dateStart);
        dest.writeString(dateEnd);
        dest.writeString(timeStart);
        dest.writeString(registrationDeadline);
        dest.writeString(location);
        dest.writeString(imageUrl);
        dest.writeByte((byte) (isFeatured ? 1 : 0));
        dest.writeString(collegeId);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<Event> CREATOR = new Creator<Event>() {
        @Override
        public Event createFromParcel(Parcel in) {
            return new Event(in);
        }

        @Override
        public Event[] newArray(int size) {
            return new Event[size];
        }
    };

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public String getDateStart() { return dateStart; }
    public String getDateEnd() { return dateEnd; }
    public String getTimeStart() { return timeStart; }
    public String getRegistrationDeadline() { return registrationDeadline; }
    public String getLocation() { return location; }
    public String getCollegeId() { return collegeId; }
    public String getImageUrl() { return imageUrl; }
    public boolean isFeatured() { return isFeatured; }
    public double getPrice() { return price; }
    public int getCapacity() { return capacity; }

    // Setters — used by the admin edit card to keep the in-memory model in sync
    // with a saved row (so a RecyclerView rebind shows the latest values).
    public void setTitle(String title) { this.title = title; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    public void setDescription(String description) { this.description = description; }
    public void setCategory(String category) { this.category = category; }
    public void setPrice(double price) { this.price = price; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public void setDateStart(String dateStart) { this.dateStart = dateStart; }
    public void setDateEnd(String dateEnd) { this.dateEnd = dateEnd; }
    public void setTimeStart(String timeStart) { this.timeStart = timeStart; }
    public void setRegistrationDeadline(String registrationDeadline) { this.registrationDeadline = registrationDeadline; }
    public void setLocation(String location) { this.location = location; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setFeatured(boolean featured) { isFeatured = featured; }
}
