package com.ahsailabs.sqlwsampleproject;

import com.ahsailabs.sqlwannotation.Column;
import com.ahsailabs.sqlwannotation.Index;
import com.ahsailabs.sqlwannotation.Table;
import com.ahsailabs.sqlwannotation.Unique;
import com.zaitunlabs.zlcore.utils.SQLiteWrapper;

import java.util.Date;

/**
 * Created by ahmad s on 2020-05-25.
 */


@Table(name="Response", recordLog=false, softDelete=true)
@Index(first="status,time")
@Unique(first="status,time")
public class Response extends SQLiteWrapper.TableClass {
    @Column(name="status", index=true, unique=true, notNull=true)
    public String status;

    public int time;

    @Column(name="tanggal")
    public Date date;

    @Column
    public float yesTime;
}
