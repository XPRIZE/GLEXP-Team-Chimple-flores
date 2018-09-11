package org.chimple.flores.sync.Direct;

public class P2PSyncService {
    private String instanceName;
    private String serviceType;
    private String deviceAddress;
    private String deviceName;

    public P2PSyncService(String instance, String type, String address, String name){
        this.instanceName = instance;
        this.serviceType = type;
        this.deviceAddress = address;
        this.deviceName =  name;
    }

    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public void setDeviceAddress(String deviceAddress) {
        this.deviceAddress = deviceAddress;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }


    public String getInstanceName() {
        return instanceName;
    }

    public String getServiceType() {
        return serviceType;
    }

    public String getDeviceAddress() {
        return deviceAddress;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String print() {
        return "instanceName: " + instanceName + " serviceType: " + serviceType + " deviceAddress: " + deviceAddress + " deviceName: " + deviceName;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }
        final P2PSyncService info = (P2PSyncService) obj;
        if (this == info) {
            return true;
        } else {
            return (this.instanceName.equals(info.instanceName) && this.serviceType == info.serviceType && this.deviceAddress == info.deviceAddress && this.deviceName == info.deviceName);
        }
    }

    @Override
    public int hashCode() {
        int hashno = 7;
        hashno = 13 * hashno + (instanceName == null ? 0 : instanceName.hashCode()) + (serviceType == null ? 0 : serviceType.hashCode()) + (deviceAddress == null ? 0 : deviceAddress.hashCode() + (deviceName == null ? 0 : deviceName.hashCode()));
        return hashno;
    }
}
