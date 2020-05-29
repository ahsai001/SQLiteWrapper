package com.ahsailabs.sqlwsampleproject;

import com.ahsailabs.sqlwannotation.Column;
import com.ahsailabs.sqlwannotation.Index;
import com.ahsailabs.sqlwannotation.Table;
import com.ahsailabs.sqlwannotation.Unique;
import com.zaitunlabs.zlcore.utils.SQLiteWrapper;

import java.util.Date;
import java.util.List;

/**
 * Created by ahmad s on 2020-05-25.
 */


@Table
@Index(first="status,time")
public class Request extends SQLiteWrapper.TableClass {
    @Column
    public int time;

    @Column
    private Date startDate;

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
