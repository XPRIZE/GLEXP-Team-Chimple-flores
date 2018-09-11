package org.chimple.flores.sync.NSD;

import java.net.InetAddress;

public class NSDSyncService {
    private String instanceName;
    private String serviceType;
    private InetAddress deviceAddress;
    private int port;

    public NSDSyncService(String instance, String type, InetAddress address, int port) {
        this.instanceName = instance;
        this.serviceType = type;
        this.deviceAddress = address;
        this.port = port;
    }

    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public void setDeviceAddress(InetAddress deviceAddress) {
        this.deviceAddress = deviceAddress;
    }


    public String getInstanceName() {
        return instanceName;
    }

    public String getServiceType() {
        return serviceType;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public InetAddress getDeviceAddress() {
        return deviceAddress;
    }




    public String print() {
        return "instanceName: " + instanceName + " serviceType: " + serviceType + " deviceAddress: " + deviceAddress;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }
        final NSDSyncService info = (NSDSyncService) obj;
        if (this == info) {
            return true;
        } else {
            return (this.instanceName.equals(info.instanceName) && this.serviceType == info.serviceType && this.deviceAddress == info.deviceAddress);
        }
    }

    @Override
    public int hashCode() {
        int hashno = 7;
        hashno = 13 * hashno + (instanceName == null ? 0 : instanceName.hashCode()) + (serviceType == null ? 0 : serviceType.hashCode()) + (deviceAddress == null ? 0 : deviceAddress.hashCode());
        return hashno;
    }
}
