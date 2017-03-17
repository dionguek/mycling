package com.example.diong.mycling;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.fourthline.cling.android.AndroidUpnpService;
import org.fourthline.cling.android.AndroidUpnpServiceImpl;
import org.fourthline.cling.model.meta.Device;
import org.fourthline.cling.model.meta.LocalDevice;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.registry.DefaultRegistryListener;
import org.fourthline.cling.registry.Registry;
import org.fourthline.cling.transport.Router;
import org.fourthline.cling.transport.RouterException;

import java.util.logging.Level;
import java.util.logging.Logger;

public class BrowserActivity extends AppCompatActivity {

    private ArrayAdapter<DeviceDisplay> listAdapter;

    private BrowseRegistryListener registryListener = new BrowseRegistryListener();

    private AndroidUpnpService upnpService;

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,IBinder service) {
            upnpService = (AndroidUpnpService)service;
            listAdapter.clear();
            upnpService.getRegistry().addListener(registryListener);
            for(Device device: upnpService.getRegistry().getDevices()){
                registryListener.deviceAdded(device);
            }
            upnpService.getControlPoint().search();
        }
        @Override
        public void onServiceDisconnected(ComponentName className){ upnpService = null;}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        listAdapter = new ArrayAdapter<>(this, R.layout.text_item);
        ListView listView = (ListView) findViewById(R.id.listItem);
        listView.setAdapter(listAdapter);

        getApplicationContext().bindService(
                new Intent(this,AndroidUpnpServiceImpl.class),
                serviceConnection,
                Context.BIND_AUTO_CREATE
        );

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener(){
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id){
                AlertDialog dialog = new AlertDialog.Builder(BrowserActivity.this).create();
                dialog.setTitle("Device Details");

                DeviceDisplay deviceDisplay = (DeviceDisplay) parent.getItemAtPosition(position);
                dialog.setMessage(deviceDisplay.getDetailMessage());
                dialog.show();
                TextView textView = (TextView)dialog.findViewById(android.R.id.message);
                textView.setTextSize(12);
            }
        });
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        if(upnpService != null){
            upnpService.getRegistry().removeListener(registryListener);
        }
        getApplicationContext().unbindService(serviceConnection);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        menu.add(0,0,0,R.string.searchLAN).setIcon(android.R.drawable.ic_menu_search);
        menu.add(0,1,0,R.string.switchRouter).setIcon(android.R.drawable.ic_menu_revert);
        menu.add(0,2,0,R.string.toggleDebugLogging).setIcon(android.R.drawable.ic_menu_info_details);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item){
        switch(item.getItemId()){
            case 0:
                if(upnpService == null)
                    break;
                Toast.makeText(this, R.string.searchingLAN, Toast.LENGTH_SHORT).show();
                upnpService.getRegistry().removeAllRemoteDevices();
                upnpService.getControlPoint().search();
                break;
            case 1:
                if(upnpService != null) {
                    Router router = upnpService.get().getRouter();
                    try{
                        if(router.isEnabled()){
                            Toast.makeText(this, R.string.disablingRouter, Toast.LENGTH_SHORT).show();
                            router.disable();
                        } else {
                            Toast.makeText(this, R.string.enablingRouter, Toast.LENGTH_SHORT).show();
                            router.enable();
                        }
                    } catch(RouterException ex){
                        Toast.makeText(this, getText(R.string.errorSwitchingRouter)+ex.toString(),Toast.LENGTH_LONG).show();
                        ex.printStackTrace(System.err);
                    }
                }
                break;
            case 2:
                Logger logger = Logger.getLogger("org.fourthline.cling");
                if(logger.getLevel() != null && !logger.getLevel().equals(Level.INFO)){
                    Toast.makeText(this,R.string.disablingDebugLogging, Toast.LENGTH_SHORT).show();
                    logger.setLevel(Level.INFO);
                }else{
                    Toast.makeText(this, R.string.enablingDebugLogging, Toast.LENGTH_SHORT).show();
                    logger.setLevel(Level.FINEST);
                }
                break;
        }
        return false;
    }

    protected class DeviceDisplay {
        Device device;

        public DeviceDisplay(Device device) {
            this.device = device;
        }

        public Device getDevice() {
            return device;
        }

        public String getDetailMessage() {
            StringBuilder sb = new StringBuilder();
            if (getDevice().isFullyHydrated()) {
                sb.append(getDevice().getDisplayString());
                sb.append("\n\n");
                for (Service service : getDevice().getServices()) {
                    sb.append(service.getServiceType()).append("\n");
                }
            } else {
                sb.append(getString(R.string.deviceDetailsNotYetAvailable));
            }
            return sb.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DeviceDisplay that = (DeviceDisplay) o;
            return device.equals(that.device);
        }

        @Override
        public int hashCode() {
            return device.hashCode();
        }

        @Override
        public String toString() {
            String name =
                    getDevice().getDetails() != null && getDevice().getDetails().getFriendlyName() != null
                            ? getDevice().getDetails().getFriendlyName()
                            : getDevice().getDisplayString();
            return device.isFullyHydrated() ? name : name + " *";
        }
    }
    protected class BrowseRegistryListener extends DefaultRegistryListener{
        @Override
        public void remoteDeviceDiscoveryStarted(Registry registry, RemoteDevice device){
            deviceAdded(device);
        }
        @Override
        public void remoteDeviceDiscoveryFailed(Registry registre, final RemoteDevice device, final Exception ex){
            runOnUiThread(new Runnable() {
                public void run(){
                    Toast.makeText(
                            BrowserActivity.this,
                            "Discovery failed of '" + device.getDisplayString() + "':"
                            + (ex != null ? ex.toString() : "Couldn't retrieve device/service descriptors"),
                            Toast.LENGTH_LONG
                    ).show();
                }
            });
            deviceRemoved(device);
        }


        @Override
        public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
            deviceAdded(device);
        }

        @Override
        public void remoteDeviceRemoved(Registry registry, RemoteDevice device) {
            deviceRemoved(device);
        }

        @Override
        public void localDeviceAdded(Registry registry, LocalDevice device) {
            deviceAdded(device);
        }

        @Override
        public void localDeviceRemoved(Registry registry, LocalDevice device) {
            deviceRemoved(device);
        }

        public void deviceAdded(final Device device){
            runOnUiThread(new Runnable(){
                public void run(){
                    DeviceDisplay d = new DeviceDisplay(device);
                    int position = listAdapter.getPosition(d);
                    if(position >= 0){
                        listAdapter.remove(d);
                        listAdapter.insert(d, position);
                    } else{
                        listAdapter.add(d);
                    }
                }
            });
        }

        public void deviceRemoved(final Device device) {
            runOnUiThread(new Runnable() {
                public void run() {
                    listAdapter.remove(new DeviceDisplay(device));
                }
            });
        }
    }

    //...
}
