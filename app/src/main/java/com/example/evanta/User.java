package com.example.evanta;

public class User {
        private String uid;
        private String name;
        private String email;
        private String whatsappno;

        public User(String uid, String name, String email, String whatsappno) {
            this.uid = uid;
            this.name = name;
            this.email = email;
            this.whatsappno = whatsappno;
        }

        @com.google.gson.annotations.SerializedName("photo_url")
        private String photoUrl;

        public String getPhotoUrl() {
        return photoUrl;
        }

        public String getUid() {
            return uid;
        }

        public String getName() {
            return name;
        }

        public String getEmail() {
            return email;
        }

        public String getWhatsappno() {
            return whatsappno;
        }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    @com.google.gson.annotations.SerializedName("college_id")
    private String collegeId;

    private String branch;

    public String getCollegeId() { return collegeId; }
    public String getBranch() { return branch; }
    public void setCollegeId(String collegeId) { this.collegeId = collegeId; }
    public void setBranch(String branch) { this.branch = branch; }
    @com.google.gson.annotations.SerializedName("college_name")
    private String collegeName;

    public String getCollegeName() { return collegeName; }
    public void setCollegeName(String collegeName) { this.collegeName = collegeName; }
}
