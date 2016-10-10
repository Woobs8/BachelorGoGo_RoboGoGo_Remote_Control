package bachelorgogo.com.robotcontrolapp;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by THP on 04-10-2016.
 */

/* memberfunctions regarding parcableity adapted from the android documentation
- https://developer.android.com/reference/android/os/Parcelable.html */

public class DeviceObject implements Parcelable {
    private String mDeviceName;
    private String mDeviceAddress;

    public DeviceObject(String deviceName, String deviceAddress) {
        setName(deviceName);
        setDeviceAddress(deviceAddress);
    }

    public void setName(String name) {
        this.mDeviceName = name;
    }

    public String getName() {
        return this.mDeviceName;
    }

    public void setDeviceAddress(String address) {
        this.mDeviceAddress = address;
    }

    public String getDeviceAddress() {
        return this.mDeviceAddress;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mDeviceName);
        dest.writeString(mDeviceAddress);
    }

    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() {
        public DeviceObject createFromParcel(Parcel in) {
            return new DeviceObject(in);
        }

        public DeviceObject[] newArray(int size) {
            return new DeviceObject[size];
        }
    };

    private DeviceObject(Parcel in) {
        mDeviceName = in.readString();
        mDeviceAddress = in.readString();
    }
}
