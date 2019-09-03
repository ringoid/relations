package com.ringoid.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Profile {
    private String userId;
    private List<Photo> photos;
    private List<Message> messages;
    //need for sorting only inside engine
    private long lastMessageAt;

    private double lat;
    private double lon;
    private long lastOnlineTime;
    private double slat;
    private double slon;
    private String slocale;
    private boolean locationExist;
    private int age;
    private String sex;
    private int property;
    private int transport;
    private int income;
    private int height;
    private int educationLevel;
    private int hairColor;
    private int children;

    private String name;
    private String jobTitle;
    private String company;
    private String education;
    private String about;
    private String instagram;
    private String tikTok;
    private String whereLive;
    private String whereFrom;
    private boolean unseen;
    private String statusText;

    //These properties needs for debug mode
    private int totalScores;

    private int totalChatCount;
    private int totalChatCountScores;

    private int totalMatchesCount;
    private int totalMatchesCountScores;

    private int photosCount;
    private int photosCountScores;

    private int incomeScores;
    private int childrenScores;
    private int eduScores;
    private int cityScores;
    private int jobTitleScore;
    private int companyScores;
    private int statusScores;
    private int nameScores;

    public Profile() {
        this.photos = new ArrayList<>();
        this.messages = new ArrayList<>();
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public List<Photo> getPhotos() {
        return photos;
    }

    public void setPhotos(List<Photo> photos) {
        this.photos = photos;
    }

    public long getLastMessageAt() {
        return lastMessageAt;
    }

    public void setLastMessageAt(long lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLon() {
        return lon;
    }

    public void setLon(double lon) {
        this.lon = lon;
    }

    public long getLastOnlineTime() {
        return lastOnlineTime;
    }

    public void setLastOnlineTime(long lastOnlineTime) {
        this.lastOnlineTime = lastOnlineTime;
    }

    public double getSlat() {
        return slat;
    }

    public void setSlat(double slat) {
        this.slat = slat;
    }

    public double getSlon() {
        return slon;
    }

    public void setSlon(double slon) {
        this.slon = slon;
    }

    public String getSlocale() {
        return slocale;
    }

    public void setSlocale(String slocale) {
        this.slocale = slocale;
    }

    public boolean isLocationExist() {
        return locationExist;
    }

    public void setLocationExist(boolean locationExist) {
        this.locationExist = locationExist;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public int getProperty() {
        return property;
    }

    public void setProperty(int property) {
        this.property = property;
    }

    public int getTransport() {
        return transport;
    }

    public void setTransport(int transport) {
        this.transport = transport;
    }

    public int getIncome() {
        return income;
    }

    public void setIncome(int income) {
        this.income = income;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getEducationLevel() {
        return educationLevel;
    }

    public void setEducationLevel(int educationLevel) {
        this.educationLevel = educationLevel;
    }

    public int getHairColor() {
        return hairColor;
    }

    public void setHairColor(int hairColor) {
        this.hairColor = hairColor;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public int getChildren() {
        return children;
    }

    public void setChildren(int children) {
        this.children = children;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getEducation() {
        return education;
    }

    public void setEducation(String education) {
        this.education = education;
    }

    public String getAbout() {
        return about;
    }

    public void setAbout(String about) {
        this.about = about;
    }

    public String getInstagram() {
        return instagram;
    }

    public void setInstagram(String instagram) {
        this.instagram = instagram;
    }

    public String getTikTok() {
        return tikTok;
    }

    public void setTikTok(String tikTok) {
        this.tikTok = tikTok;
    }

    public String getWhereLive() {
        return whereLive;
    }

    public void setWhereLive(String whereLive) {
        this.whereLive = whereLive;
    }

    public String getWhereFrom() {
        return whereFrom;
    }

    public void setWhereFrom(String whereFrom) {
        this.whereFrom = whereFrom;
    }

    public boolean isUnseen() {
        return unseen;
    }

    public void setUnseen(boolean unseen) {
        this.unseen = unseen;
    }

    public String getStatusText() {
        return statusText;
    }

    public void setStatusText(String statusText) {
        this.statusText = statusText;
    }

    public int getTotalChatCount() {
        return totalChatCount;
    }

    public void setTotalChatCount(int totalChatCount) {
        this.totalChatCount = totalChatCount;
    }

    public int getTotalChatCountScores() {
        return totalChatCountScores;
    }

    public void setTotalChatCountScores(int totalChatCountScores) {
        this.totalChatCountScores = totalChatCountScores;
    }

    public int getTotalMatchesCount() {
        return totalMatchesCount;
    }

    public void setTotalMatchesCount(int totalMatchesCount) {
        this.totalMatchesCount = totalMatchesCount;
    }

    public int getTotalMatchesCountScores() {
        return totalMatchesCountScores;
    }

    public void setTotalMatchesCountScores(int totalMatchesCountScores) {
        this.totalMatchesCountScores = totalMatchesCountScores;
    }

    public int getPhotosCount() {
        return photosCount;
    }

    public void setPhotosCount(int photosCount) {
        this.photosCount = photosCount;
    }

    public int getPhotosCountScores() {
        return photosCountScores;
    }

    public void setPhotosCountScores(int photosCountScores) {
        this.photosCountScores = photosCountScores;
    }

    public int getIncomeScores() {
        return incomeScores;
    }

    public void setIncomeScores(int incomeScores) {
        this.incomeScores = incomeScores;
    }

    public int getChildrenScores() {
        return childrenScores;
    }

    public void setChildrenScores(int childrenScores) {
        this.childrenScores = childrenScores;
    }

    public int getEduScores() {
        return eduScores;
    }

    public void setEduScores(int eduScores) {
        this.eduScores = eduScores;
    }

    public int getCityScores() {
        return cityScores;
    }

    public void setCityScores(int cityScores) {
        this.cityScores = cityScores;
    }

    public int getJobTitleScore() {
        return jobTitleScore;
    }

    public void setJobTitleScore(int jobTitleScore) {
        this.jobTitleScore = jobTitleScore;
    }

    public int getCompanyScores() {
        return companyScores;
    }

    public void setCompanyScores(int companyScores) {
        this.companyScores = companyScores;
    }

    public int getStatusScores() {
        return statusScores;
    }

    public void setStatusScores(int statusScores) {
        this.statusScores = statusScores;
    }

    public int getNameScores() {
        return nameScores;
    }

    public void setNameScores(int nameScores) {
        this.nameScores = nameScores;
    }

    public int getTotalScores() {
        return totalScores;
    }

    public void setTotalScores(int totalScores) {
        this.totalScores = totalScores;
    }
}
