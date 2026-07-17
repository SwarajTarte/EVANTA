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
}
