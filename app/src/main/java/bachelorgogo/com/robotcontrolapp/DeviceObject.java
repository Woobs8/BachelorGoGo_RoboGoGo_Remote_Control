package bachelorgogo.com.robotcontrolapp;

/**
 * Created by THP on 04-10-2016.
 */

public class DeviceObject {
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
}
