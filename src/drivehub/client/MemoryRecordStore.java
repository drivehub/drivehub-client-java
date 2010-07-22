package drivehub.client;

import java.util.Enumeration;
import java.util.Vector;

public class MemoryRecordStore implements SensorRecordStore {

    Vector recordStore = new Vector();
    
    @Override
    public void deleteRecord(int id) {
        recordStore.setElementAt(null, id);
    }

    @Override
    public Enumeration enumerateRecordIDs() {
    	while(true){
    		if (!recordStore.removeElement(null)) break;
    	}
        return new Enumeration() {
            int idx = 0;
            @Override
            public boolean hasMoreElements() {
                return recordStore.size() > idx;
            }

            @Override
            public Object nextElement() {
                if (recordStore.size() <= idx) return null;
                Integer id = new Integer(idx);
                idx++;
                return id;
            }
        };
    }
    @Override
    public int getRecordsCount(){
        return recordStore.size();
    }

    @Override
    public byte[] getRecord(int id) {
        if (recordStore.size() <= id) return null;
        return (byte[])recordStore.elementAt(id);
    }

    @Override
    public void storeRecord(byte[] record, int size) {
        System.out.println("STORING "+size);
        byte[] record_dup = new byte[size];
        System.arraycopy(record, 0, record_dup, 0, size);
        recordStore.add(record_dup);
    }
    
    public void clear() {
        recordStore.clear();
    }

}
