package com.ahsailabs.sqlwsampleproject;

import com.ahsailabs.sqlitewrapper.SQLiteWrapper;
import com.ahsailabs.sqlwannotation.Column;
import com.ahsailabs.sqlwannotation.Index;
import com.ahsailabs.sqlwannotation.Table;

import java.util.Date;
import java.util.List;

/**
 * Created by ahmad s on 2020-05-25.
 */


@Table
@Index(first="status_message,time")
public class Request extends SQLiteWrapper.TableClass {
    @Column
    public int time;

    @Column
    private String statusMessage;

    @Column
    private float yesTime;

    public float getYesTime() {
        return yesTime;
    }

    public void setYesTime(float yesTime) {
        this.yesTime = yesTime;
    }

    @Column
    private Date startDate;

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    @Override
    protected void getObjectData(List<Object> dataList) {
        RequestSQLWHelper.getObjectData(dataList, this);
    }

    @Override
    protected void setObjectData(List<Object> dataList) {
        RequestSQLWHelper.setObjectData(dataList, this);
    }
}
