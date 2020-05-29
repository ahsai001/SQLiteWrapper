package com.ahsailabs.sqlitewrapper;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Created by ahsai.
 */

public final class SQLiteWrapper extends SQLiteOpenHelper {
    public static final String ID = "_id";
    public static final String CREATED_AT = "_created_at";
    public static final String UPDATED_AT = "_updated_at";
    public static final String DELETED_AT = "_deleted_at";
    private static final String TAG = SQLiteWrapper.class.getName();
    public static final String SQLW_FOLDER = "SQLW/";
    private Map<String, Table> tableMap;
    private List<Index> indexList;
    private AssetManager assetManager;

    private static Map<String, Database> sqLiteDatabaseMap = new HashMap<>();
    private static Map<String, SQLiteWrapper> sqLiteWrapperMap = new HashMap<>();

    public static void addDatabase(Database database){
        //invoke this inside Application.onCreate
        if(!sqLiteDatabaseMap.containsKey(database.getDatabaseName())) {
            sqLiteDatabaseMap.put(database.getDatabaseName(), database);
        }
    }

    public static void removeAllDatabase(){
        //invoke this inside Application.onTerminate
        for (String databaseName : sqLiteWrapperMap.keySet()){
            SQLiteWrapper sqLiteWrapper = sqLiteWrapperMap.get(databaseName);
            sqLiteWrapper.release();
            sqLiteWrapperMap.remove(databaseName);
        }

        for (String databaseName : sqLiteDatabaseMap.keySet()){
            sqLiteDatabaseMap.remove(databaseName);
        }

        sqLiteWrapperMap = null;
        sqLiteDatabaseMap = null;
    }


    public static abstract class Database {
        public Database(){}
        public abstract Context getContext();
        public abstract String getDatabaseName();
        public abstract int getDatabaseVersion();
        public abstract void configure(SQLiteWrapper sqLiteWrapper);
        private SQLiteWrapper getSQLiteWrapper(){
            if(sqLiteWrapperMap.containsKey(getDatabaseName())){
                return sqLiteWrapperMap.get(getDatabaseName());
            } else {
                if(getContext() == null) throw new RuntimeException("Please set getContext method with return context");
                SQLiteWrapper sqLiteWrapper = new Builder()
                        .setDatabaseName(getDatabaseName())
                        .setDatabaseVersion(getDatabaseVersion())
                        .create(getContext().getApplicationContext());
                configure(sqLiteWrapper);
                sqLiteWrapper.init();
                sqLiteWrapperMap.put(getDatabaseName(),sqLiteWrapper);
                return sqLiteWrapper;
            }
        }
    }

    public SQLiteWrapper addTable(Table table){
        tableMap.put(table.getName(), table);
        return this;
    }


    public SQLiteWrapper addTablesFromSQLAsset(String filename){
        List<Table> tableList = readAndParseCreateTableScript(filename);
        if(tableList != null) {
            for (Table table : tableList) {
                tableMap.put(table.getName(), table);
            }
        }
        return this;
    }

    public static SQLiteWrapper of(String databaseName){
        if(sqLiteDatabaseMap.containsKey(databaseName)){
            return sqLiteDatabaseMap.get(databaseName).getSQLiteWrapper();
        }
        return null;
    }

    private void init(){
    }

    private void release(){
        close();
        tableMap = null;
        indexList = null;
    }

    private SQLiteWrapper(Context context, String databaseName, int databaseVersion, Map<String, Table> tableMap, List<Index> indexList){
        super(context, databaseName, null, databaseVersion);
        this.assetManager = context.getAssets();
        this.tableMap = tableMap;
        this.indexList = indexList;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        List<String> sqlScriptList = new ArrayList<>();

        List<Index> tableIndexList = new ArrayList<>();

        if(tableMap != null){
            Set<String> keySet = tableMap.keySet();
            for (String key : keySet) {
                Table table = tableMap.get(key);
                if(table != null) {
                    String createTableScript = getCreateTableScript(table);
                    sqlScriptList.add(createTableScript);

                    tableIndexList.addAll(table.getIndexList());
                }
            }
        }

        if(indexList != null){
            for (Index index : indexList){
                String createIndexScript = getCreateIndexScript(index);
                sqlScriptList.add(createIndexScript);
            }
        }

        for (Index index : tableIndexList){
            String createIndexScript = getCreateIndexScript(index);
            sqlScriptList.add(createIndexScript);
        }

        db.beginTransaction();
        try{
            for (String sqlScript : sqlScriptList){
                db.execSQL(sqlScript);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private String getCreateIndexScript(Index index) {
        if(index == null) return null;
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("CREATE INDEX ").append("idx_").append(index.getTableName()).append("_")
                .append(TextUtils.join("_",index.getColumnList()));
        stringBuilder.append(" ON ").append(index.getTableName()).append(" (")
                .append(TextUtils.join(", ",index.getColumnList())).append(")");

        return stringBuilder.toString();
    }


    private String getCreateTableScript(Table table){
        if(table == null) return null;
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("CREATE TABLE ").append(table.getName()).append(" (");
        stringBuilder.append(ID).append(" ").append(Field.INTEGER).append(" ").append("PRIMARY KEY")
                .append(" ").append("AUTOINCREMENT");

        List<Field> fieldList = table.getFieldList();
        for (Field field : fieldList) {
            stringBuilder.append(",");
            stringBuilder.append(field.getName()).append(" ").append(field.getType());

            if(field.isNotNull()){
                stringBuilder.append(" NOT NULL");
            }

            if(field.isUnique()){
                stringBuilder.append(" UNIQUE");
            }

            //extra like default etc
            if (field.getTrueType() == boolean.class) {
                stringBuilder.append(" DEFAULT 0");
            }
        }

        if(table.isRecordLogEnabled){
            stringBuilder.append(",").append(CREATED_AT).append(" ").append(Field.INTEGER);
            stringBuilder.append(",").append(UPDATED_AT).append(" ").append(Field.INTEGER);
        }

        if(table.isSoftDeleteEnabled){
            stringBuilder.append(",").append(DELETED_AT).append(" ").append(Field.INTEGER);
        }

        List<Unique> uniqueList = table.getUniqueList();
        for (Unique unique : uniqueList){
            stringBuilder.append(",UNIQUE(").append(TextUtils.join(",", unique.getColumnList())).append(")");
        }

        List<Check> checkList = table.getCheckList();
        for (Check check : checkList){
            stringBuilder.append(",CHECK(").append(check.getConditionalLogic()).append(")");
        }

        List<ForeignKey> foreignKeyList = table.getForeignKeyList();
        for (ForeignKey foreignKey : foreignKeyList){
            stringBuilder.append(",FOREIGN KEY (").append(foreignKey.getChildColumnName()).append(") ")
                    .append("REFERENCES ").append(foreignKey.getParentTableName()).append(" ")
                    .append("(").append(foreignKey.getParentColumnName()).append(") ")
                    .append("ON UPDATE ").append(foreignKey.getOnUpdateAction()).append(" ")
                    .append("ON DELETE ").append(foreignKey.getOnDeleteAction());
        }

        stringBuilder.append(")");

        return stringBuilder.toString();
    }


    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        try {
            List<String> upgradeScriptList = new ArrayList<>();
            for (int i = oldVersion; i < newVersion; ++i) {
                if(migrationPlan != null){
                    List<MigrationStep> migrationStepList = migrationPlan.getUpgradePlan(i,(i+1));
                    if(migrationStepList != null && migrationStepList.size() > 0){
                        for (MigrationStep migrationStep : migrationStepList){
                            List<String> sqlScriptList = migrationStep.getSQLScriptList(this);
                            if(sqlScriptList != null && sqlScriptList.size() > 0) {
                                for (String sqlScript : sqlScriptList) {
                                    if(!TextUtils.isEmpty(sqlScript)) {
                                        upgradeScriptList.add(sqlScript);
                                    }
                                }
                            }
                        }
                        continue;
                    }
                }

                String migrationFileName = String.format("%d-%d-%s.sql", i, (i + 1), getDatabaseName());
                Log.d(TAG, "Looking for migration file: " + migrationFileName);
                List<String> resultList = readAndGetSQLScript(migrationFileName);
                upgradeScriptList.addAll(resultList);

            }

            db.beginTransaction();
            try{
                for (String upgradeScript : upgradeScriptList){
                    db.execSQL(upgradeScript);
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }

        } catch (Exception exception) {
            Log.e(TAG, "Exception running upgrade script:", exception);
        }
    }


    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        try {
            List<String> downgradeScriptList = new ArrayList<>();
            for (int i = oldVersion; i > newVersion; --i) {
                if(migrationPlan != null){
                    List<MigrationStep> migrationStepList = migrationPlan.getDowngradePlan(i,(i-1));
                    if(migrationStepList != null && migrationStepList.size() > 0){
                        for (MigrationStep migrationStep : migrationStepList){
                            List<String> sqlScriptList = migrationStep.getSQLScriptList(this);
                            if(sqlScriptList != null && sqlScriptList.size() > 0) {
                                for (String sqlScript : sqlScriptList) {
                                    if(!TextUtils.isEmpty(sqlScript)) {
                                        downgradeScriptList.add(sqlScript);
                                    }
                                }
                            }
                        }
                        continue;
                    }
                }

                String migrationFileName = String.format("%d-%d-%s.sql", i, (i - 1), getDatabaseName());
                Log.d(TAG, "Looking for migration file: " + migrationFileName);
                List<String> resultList = readAndGetSQLScript(migrationFileName);
                downgradeScriptList.addAll(resultList);

            }

            db.beginTransaction();
            try{
                for (String downgradeScript : downgradeScriptList){
                    db.execSQL(downgradeScript);
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }

        } catch (Exception exception) {
            Log.e(TAG, "Exception running downgrade script:", exception);
        }
    }



    private ContentValues getContentValues(Table table, List<Object> dataList){
        ContentValues contentValues = new ContentValues();
        List<Field> fieldList = table.getFieldList();
        //List<ForeignKey> foreignKeyList = table.getForeignKeyList();

        for (int i = 0; i < fieldList.size(); i++) {
            Field field = fieldList.get(i);

            Object data = dataList.get(i);

            if(data == null)continue;

            switch (field.getType()) {
                case Field.TEXT:
                    contentValues.put(field.getName(), (String) data);
                    break;
                case Field.INTEGER:
                    if (field.getTrueType() == int.class || field.getTrueType() == Integer.class) {
                        contentValues.put(field.getName(), (Integer) data);
                    } else if (field.getTrueType() == long.class || field.getTrueType() == Long.class) {
                        if(data instanceof TableClass){
                            contentValues.put(field.getName(), ((TableClass)data)._id);
                        } else {
                            contentValues.put(field.getName(), (Long) data);
                        }
                    } else if (field.getTrueType() == boolean.class || field.getTrueType() == Boolean.class) {
                        contentValues.put(field.getName(), (Boolean) data);
                    } else if (field.getTrueType() == Date.class) {
                        contentValues.put(field.getName(), ((Date) data).getTime());
                    }
                    break;
                case Field.REAL:
                    if (field.getTrueType() == float.class || field.getTrueType() == Float.class) {
                        contentValues.put(field.getName(), (Float) data);
                    } else if (field.getTrueType() == double.class || field.getTrueType() == Double.class) {
                        contentValues.put(field.getName(), (Double) data);
                    }
                    break;
                case Field.BLOB:
                    //TODO:
                    //contentValues.put(field.getName(), (String) objectList.of(i));
                    break;
            }
        }

        return contentValues;
    }


    private List<Object> fetchRow(Cursor cursor, String tableName){
        List<Object> dataList = new ArrayList<>();
        Table table = tableMap.get(tableName);
        List<Field> fieldList =  table.getFieldList();

        List<ForeignKey> foreignKeyList = table.getForeignKeyList();
        List<String> childColumnNameList = new ArrayList<>();
        for (ForeignKey foreignKey : foreignKeyList){
            childColumnNameList.add(foreignKey.getChildColumnName());
        }

        for (int i = 0; i < fieldList.size(); i++) {
            Field field = fieldList.get(i);
            int cursorIndex = cursor.getColumnIndex(field.getName());
            switch (field.getType()) {
                case Field.TEXT:
                    dataList.add(cursor.isNull(cursorIndex)?null:cursor.getString(cursorIndex));
                    break;
                case Field.INTEGER:
                    if (field.getTrueType() == int.class || field.getTrueType() == Integer.class) {
                        dataList.add(cursor.isNull(cursorIndex)?null:cursor.getInt(cursorIndex));
                    } else if (field.getTrueType() == long.class || field.getTrueType() == Long.class) {
                        if(cursor.isNull(cursorIndex)){
                            dataList.add(null);
                        } else {
                            if (childColumnNameList.contains(field.getName())) {
                                int foreignIndex = childColumnNameList.indexOf(field.getName());
                                ForeignKey foreignKey = foreignKeyList.get(foreignIndex);

                                dataList.add(findFirstWithCriteria(foreignKey.getParentTableName(), foreignKey.getParentTableClass(),
                                        foreignKey.getParentColumnName() + "=?", new String[]{Long.toString(cursor.getLong(cursorIndex))}));

                            } else {
                                dataList.add(cursor.getLong(cursorIndex));
                            }
                        }
                    } else if (field.getTrueType() == boolean.class || field.getTrueType() == Boolean.class) {
                        dataList.add(cursor.isNull(cursorIndex)?null:(cursor.getInt(cursorIndex) == 1));
                    } else if (field.getTrueType() == Date.class) {
                        if(cursor.isNull(cursorIndex)){
                            dataList.add(null);
                        } else {
                            long dateLong = cursor.getLong(cursorIndex);
                            dataList.add(new Date(dateLong));
                        }
                    }
                    break;
                case Field.REAL:
                    if (field.getTrueType() == float.class || field.getTrueType() == Float.class) {
                        dataList.add(cursor.isNull(cursorIndex)?null:cursor.getFloat(cursorIndex));
                    } else if (field.getTrueType() == double.class || field.getTrueType() == Double.class) {
                        dataList.add(cursor.isNull(cursorIndex)?null:cursor.getDouble(cursorIndex));
                    }
                    break;
                case Field.BLOB:
                    //TODO :
                    break;
            }

        }
        return dataList;
    }


    private <T extends TableClass> void fetchRecordLog(Table table, T tableClass, Cursor cursor) {
        if(table.isRecordLogEnabled){
            int createdAtColumnIndex = cursor.getColumnIndex(CREATED_AT);
            if(!cursor.isNull(createdAtColumnIndex)) {
                long createdAtLong = cursor.getLong(createdAtColumnIndex);
                tableClass._created_at = new Date(createdAtLong);
            }

            int updatedAtColumnIndex = cursor.getColumnIndex(UPDATED_AT);
            if(!cursor.isNull(updatedAtColumnIndex)) {
                long updatedAtLong = cursor.getLong(updatedAtColumnIndex);
                tableClass._updated_at = new Date(updatedAtLong);
            }
        }
    }

    //create or insert
    private boolean save(TableClass tableClass) {
        long id = -1;
        try {
            SQLiteDatabase database = getDatabase(false);

            Table table = tableMap.get(tableClass.getTableName());

            List<Object> dataList = new ArrayList<>();
            tableClass.getObjectData(dataList);

            ContentValues contentValues = getContentValues(table, dataList);

            if(table.isRecordLogEnabled){
                contentValues.put(CREATED_AT, System.currentTimeMillis());
            }

            id = database.insert(tableClass.getTableName(), null, contentValues);

            closeDatabase();

            if(id <= 0){
                return false;
            }
        } catch (SQLException e){
            return false;
        }

        tableClass._id = id;
        return true;
    }



    //update
    private boolean update(TableClass tableClass) {
        long affectedRows = -1;
        try {
            SQLiteDatabase database = getDatabase(false);

            Table table = tableMap.get(tableClass.getTableName());

            List<Object>  dataList = new ArrayList<>();
            tableClass.getObjectData(dataList);

            ContentValues contentValues = getContentValues(table, dataList);

            if(table.isRecordLogEnabled){
                contentValues.put(UPDATED_AT, System.currentTimeMillis());
            }

            affectedRows = database.update(tableClass.getTableName(), contentValues, ID+"=?",
                    new String[]{Long.toString(tableClass._id)});

            closeDatabase();

            if(affectedRows <= 0){
                return false;
            }
        } catch (SQLException e){
            return false;
        }
        return true;
    }


    public <T extends TableClass> boolean update(String tableName, Class<T> clazz,  ContentValues contentValues, String whereClause, String[] whereClauseArgs){
        if(TextUtils.isEmpty(tableName)){
            tableName = clazz.getSimpleName();
        }
        long affectedRows = -1;
        try {
            SQLiteDatabase database = getDatabase(false);

            affectedRows = database.update(tableName, contentValues, whereClause, whereClauseArgs);

            closeDatabase();

            if(affectedRows <= 0){
                return false;
            }
        } catch (SQLException e){
            return false;
        }
        return true;
    }



        //delete
    private boolean delete(TableClass tableClass) {
        if(tableClass._id > 0) {
            try {
                SQLiteDatabase database = getDatabase(false);

                Table table = tableMap.get(tableClass.getTableName());

                int affectedRows;

                if(table.isSoftDeleteEnabled){
                    ContentValues contentValues = new ContentValues();
                    contentValues.put(DELETED_AT, System.currentTimeMillis());
                    affectedRows = database.update(tableClass.getTableName(), contentValues, ID+"=?",
                            new String[]{Long.toString(tableClass._id)});
                } else {
                    affectedRows = database.delete(tableClass.getTableName(), ID + "=?",
                            new String[]{Long.toString(tableClass._id)});
                }

                closeDatabase();

                if(affectedRows <= 0){
                    return false;
                }
            } catch (SQLException e){
                return false;
            }

            return true;
        }
        return false;
    }


    public <T extends TableClass> void delete(String tableName, Class<T> clazz,  String whereClause, String[] whereClauseArgs){
        if(TextUtils.isEmpty(tableName)){
            tableName = clazz.getSimpleName();
        }
        try {
            SQLiteDatabase database = getDatabase(false);

            Table table = tableMap.get(tableName);
            if(table.isSoftDeleteEnabled){
                ContentValues contentValues = new ContentValues();
                contentValues.put(DELETED_AT, System.currentTimeMillis());
                database.update(tableName, contentValues, whereClause, whereClauseArgs);
            } else {
                database.delete(tableName, whereClause, whereClauseArgs);
            }
            closeDatabase();
        } catch (SQLException e){
            e.printStackTrace();
        }
    }


    public <T extends TableClass> void deleteAll(String tableName, Class<T> clazz) {
        if(TextUtils.isEmpty(tableName)){
            tableName = clazz.getSimpleName();
        }
        delete(tableName, clazz, null, null);
    }




    private RuntimeException getWarningWhenNoConstructorWithNoArgument(String className){
        return new RuntimeException(String.format(
                "Class %s must has constructor with no argument",className));
    }

    public <T extends TableClass> T findById(long id, String tableName, Class<T> clazz){
        try {
            if(TextUtils.isEmpty(tableName)){
                tableName = clazz.getSimpleName();
            }
            SQLiteDatabase database = getDatabase(true);

            Table table = tableMap.get(tableName);

            String sql = "SELECT * FROM "+tableName+" WHERE "+ID+"=?";

            if(table.isSoftDeleteEnabled){
                sql += " AND "+DELETED_AT+" IS NULL";
            }

            Cursor cursor = database.rawQuery(sql, new String[]{Long.toString(id)});

            T tableClass = null;

            if(cursor != null) {
                cursor.moveToFirst();

                List<Object> dataList = fetchRow(cursor, tableName);

                tableClass = clazz.newInstance();
                tableClass._id = cursor.getLong(cursor.getColumnIndex(ID));
                tableClass.setObjectData(dataList);

                fetchRecordLog(table, tableClass, cursor);

                closeCursor(cursor);
            }

            closeDatabase();

            return tableClass;
        } catch (SQLException e){
            return null;
        } catch (InstantiationException e){
            throw getWarningWhenNoConstructorWithNoArgument(clazz.getSimpleName());
        } catch (IllegalAccessException e){
            return null;
        }
    }



    public <T extends TableClass> List<T> findAll(String tableName, Class<T> clazz) {
        try {
            if(TextUtils.isEmpty(tableName)){
                tableName = clazz.getSimpleName();
            }

            SQLiteDatabase database = getDatabase(true);

            Table table = tableMap.get(tableName);

            String sql = "SELECT * FROM " + tableName;

            if(table.isSoftDeleteEnabled){
                sql += " WHERE "+DELETED_AT+" IS NULL";
            }

            Cursor cursor = database.rawQuery(sql, null);

            List<T> resultList = new ArrayList<>();

            if(cursor != null) {
                cursor.moveToFirst();

                while (!cursor.isAfterLast()) {
                    List<Object> dataList = fetchRow(cursor, tableName);

                    T tableClass = clazz.newInstance();
                    tableClass._id = cursor.getLong(cursor.getColumnIndex(ID));
                    tableClass.setObjectData(dataList);

                    fetchRecordLog(table, tableClass, cursor);

                    resultList.add(tableClass);

                    cursor.moveToNext();
                }

                closeCursor(cursor);
            }
            closeDatabase();

            return resultList;
        } catch (SQLException e){
            return null;
        } catch (InstantiationException e){
            throw getWarningWhenNoConstructorWithNoArgument(clazz.getSimpleName());
        } catch (IllegalAccessException e){
            return null;
        }
    }

    public <T extends TableClass> T findFirst(String tableName, Class<T> clazz) {
        List<T> resultList = selectQuery(false,tableName,clazz,null,null,null,
                null,null,ID+" ASC","1");
        if(resultList != null && resultList.size() > 0){
            return resultList.get(0);
        }
        return  null;
    }

    public <T extends TableClass> T findLast(String tableName, Class<T> clazz) {
        List<T> resultList = selectQuery(false,tableName,clazz,null,null,null,
                null,null,ID+" DESC","1");
        if(resultList != null && resultList.size() > 0){
            return resultList.get(resultList.size()-1);
        }
        return  null;
    }

    public <T extends TableClass> List<T> findAllWithCriteria(String tableName, Class<T> clazz, String whereClause, String[] whereClauseArgs) {
        try {
            if(TextUtils.isEmpty(tableName)){
                tableName = clazz.getSimpleName();
            }

            SQLiteDatabase database = getDatabase(true);

            Table table = tableMap.get(tableName);

            String sql = "SELECT * FROM " + tableName + " WHERE ("+whereClause+")";

            if(table.isSoftDeleteEnabled){
                sql += " AND "+DELETED_AT+" IS NULL";
            }

            Cursor cursor = database.rawQuery(sql, whereClauseArgs);

            List<T> resultList = new ArrayList<>();

            if(cursor != null) {
                cursor.moveToFirst();
                while (!cursor.isAfterLast()) {
                    List<Object> dataList = fetchRow(cursor, tableName);

                    T tableClass = clazz.newInstance();
                    tableClass._id = cursor.getLong(cursor.getColumnIndex(ID));
                    tableClass.setObjectData(dataList);

                    fetchRecordLog(table, tableClass, cursor);

                    resultList.add(tableClass);

                    cursor.moveToNext();
                }
                closeCursor(cursor);
            }
            closeDatabase();

            return resultList;
        } catch (SQLException e){
            return null;
        } catch (InstantiationException e){
            throw getWarningWhenNoConstructorWithNoArgument(clazz.getSimpleName());
        } catch (IllegalAccessException e){
            return null;
        }
    }

    public <T extends TableClass> T findFirstWithCriteria(String tableName, Class<T> clazz, String whereClause, String[] whereClauseArgs) {
        List<T> resultList = selectQuery(false,tableName,clazz,null,whereClause,whereClauseArgs,
                null,null,ID+" ASC","1");
        if(resultList != null && resultList.size() > 0){
            return resultList.get(0);
        }
        return  null;
    }

    public <T extends TableClass> T findLastWithCriteria(String tableName, Class<T> clazz, String whereClause, String[] whereClauseArgs) {
        List<T> resultList = selectQuery(false,tableName,clazz,null,whereClause,whereClauseArgs,
                null,null,ID+" DESC","1");
        if(resultList != null && resultList.size() > 0){
            return resultList.get(resultList.size()-1);
        }
        return  null;
    }


    public <T extends TableClass> List<T> selectQuery(boolean distinct, String tableName, Class<T> clazz, String[] columns,
                                            String selection, String[] selectionArgs, String groupBy,
                                            String having, String orderBy, String limit) {
        try {
            if(TextUtils.isEmpty(tableName)){
                tableName = clazz.getSimpleName();
            }

            SQLiteDatabase database = getDatabase(true);

            Table table = tableMap.get(tableName);

            if(table.isSoftDeleteEnabled){
                if(TextUtils.isEmpty(selection)){
                    selection = DELETED_AT+" IS NULL";
                } else {
                    selection = "("+selection+") AND "+DELETED_AT+" IS NULL";
                }
            }

            Cursor cursor = database.query(distinct, tableName, columns, selection, selectionArgs, groupBy, having, orderBy, limit);

            List<T> resultList = new ArrayList<>();

            if(cursor != null) {
                cursor.moveToFirst();
                while (!cursor.isAfterLast()) {
                    List<Object> dataList = fetchRow(cursor, tableName);

                    T tableClass = clazz.newInstance();
                    tableClass._id = cursor.getLong(cursor.getColumnIndex(ID));
                    tableClass.setObjectData(dataList);

                    fetchRecordLog(table, tableClass, cursor);

                    resultList.add(tableClass);

                    cursor.moveToNext();
                }

                closeCursor(cursor);
            }
            closeDatabase();

            return resultList;
        } catch (SQLException e){
            return null;
        } catch (InstantiationException e){
            throw getWarningWhenNoConstructorWithNoArgument(clazz.getSimpleName());
        } catch (IllegalAccessException e){
            return null;
        }
    }


    public <T extends TableClass> List<T> rawSelectQuery(Class<T> clazz, String selectSql, String[] sqlArgs) {
        try {
            String tableName = substringBetween(" from "," ", selectSql.replace("FROM","from")+" ");

            SQLiteDatabase database = getDatabase(true);

            Cursor cursor = database.rawQuery(selectSql, sqlArgs);

            List<T> resultList = new ArrayList<>();

            if(cursor != null) {
                cursor.moveToFirst();

                Table table = tableMap.get(tableName);

                while (!cursor.isAfterLast()) {
                    List<Object> dataList = fetchRow(cursor, tableName);

                    T tableClass = clazz.newInstance();
                    tableClass._id = cursor.getLong(cursor.getColumnIndex(ID));
                    tableClass.setObjectData(dataList);

                    fetchRecordLog(table, tableClass, cursor);

                    resultList.add(tableClass);

                    cursor.moveToNext();
                }

                closeCursor(cursor);
            }
            closeDatabase();

            return resultList;
        } catch (SQLException e){
            return null;
        } catch (InstantiationException e){
            throw getWarningWhenNoConstructorWithNoArgument(clazz.getSimpleName());
        } catch (IllegalAccessException e){
            return null;
        }
    }


    public <T extends TableClass> long count(String tableName, Class<T> clazz, String selection,
                                            String[] selectionArgs){
        if(TextUtils.isEmpty(tableName)){
            tableName = clazz.getSimpleName();
        }
        SQLiteDatabase database = getDatabase(true);
        long count = DatabaseUtils.queryNumEntries(database, tableName, selection, selectionArgs);
        closeDatabase();
        return count;
    }


    public void execSQL(String sql, String[] sqlArgs) {
        try {
            SQLiteDatabase database = getDatabase(false);
            if(sqlArgs == null){
                sqlArgs = new String[]{};
            }
            database.execSQL(sql, sqlArgs);
            closeDatabase();
        } catch (SQLException e){
            e.printStackTrace();
        }
    }

    private SQLiteDatabase getDatabase(boolean isReadOnly){
        if(isReadOnly){
            return getReadableDatabase();
        } else {
            return getWritableDatabase();
        }
    }

    private void closeDatabase(){
        //close(); //no need to call this, this will
        // make "java.lang.IllegalStateException:
        // Cannot perform this operation because the connection pool has been closed."
        // when access in multiple thread

        //database connection is singleton, life cycle scope is entire application
    }

    private void closeCursor(Cursor cursor){
        if(cursor != null && !cursor.isClosed()){
            cursor.close();
        }
    }

    private synchronized void runQueryInBatch(BatchProcess batchProcess){
        SQLiteDatabase database = getDatabase(false);
        database.beginTransaction();
        try{
            batchProcess.onProcess(this);
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }


    public interface BatchProcess{
        void onProcess(SQLiteWrapper sqLiteWrapper);
    }



    public static class Table{
        private String name;
        private boolean isSoftDeleteEnabled = false;
        private boolean isRecordLogEnabled = false;


        private List<Field> fieldList = new ArrayList<>();
        private List<Index> indexList = new ArrayList<>();
        private List<Unique> uniqueList = new ArrayList<>();
        private List<ForeignKey> foreignKeyList = new ArrayList<>();
        private List<Check> checkList = new ArrayList<>();

        public Table(String name) {
            this.name = name;
        }

        public Table(Class clazz) {
            this.name = clazz.getSimpleName();
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<Field> getFieldList() {
            return fieldList;
        }

        public Table addField(Field field){
            fieldList.add(field);
            return this;
        }

        private void setNullState(Field field, boolean isNotNull){
            if(isNotNull){
                field.setNotNull();
            } else {
                field.setNullable();
            }
        }

        public Table addIntField(String name, boolean isNotNull, boolean isUnique){
            Field field = new Field(name, Field.INTEGER, int.class);
            setNullState(field, isNotNull);
            field.setUnique(isUnique);
            fieldList.add(field);
            return this;
        }

        public Table addIntField(String name){
            addIntField(name, false, false);
            return this;
        }

        public Table addLongField(String name, boolean isNotNull, boolean isUnique){
            Field field = new Field(name, Field.INTEGER, long.class);
            setNullState(field, isNotNull);
            field.setUnique(isUnique);
            fieldList.add(field);
            return this;
        }

        public Table addLongField(String name){
            addLongField(name, false, false);
            return this;
        }

        public Table addStringField(String name, boolean isNotNull, boolean isUnique){
            Field field = new Field(name, Field.TEXT, String.class);
            setNullState(field, isNotNull);
            field.setUnique(isUnique);
            fieldList.add(field);
            return this;
        }

        public Table addStringField(String name){
            addStringField(name, false, false);
            return this;
        }


        public Table addFloatField(String name, boolean isNotNull, boolean isUnique){
            Field field = new Field(name, Field.REAL, float.class);
            setNullState(field, isNotNull);
            field.setUnique(isUnique);
            fieldList.add(field);
            return this;
        }

        public Table addFloatField(String name){
            addFloatField(name, false, false);
            return this;
        }

        public Table addDoubleField(String name, boolean isNotNull, boolean isUnique){
            Field field = new Field(name, Field.REAL, double.class);
            setNullState(field, isNotNull);
            field.setUnique(isUnique);
            fieldList.add(field);
            return this;
        }

        public Table addDoubleField(String name){
            addDoubleField(name, false, false);
            return this;
        }

        public Table addBooleanField(String name, boolean isNotNull, boolean isUnique){
            Field field = new Field(name, Field.INTEGER, boolean.class);
            setNullState(field, isNotNull);
            field.setUnique(isUnique);
            fieldList.add(field);
            return this;
        }

        public Table addBooleanField(String name){
            addBooleanField(name, false, false);
            return this;
        }

        public Table addDateField(String name, boolean isNotNull, boolean isUnique){
            Field field = new Field(name, Field.INTEGER, Date.class);
            setNullState(field, isNotNull);
            field.setUnique(isUnique);
            fieldList.add(field);
            return this;
        }

        public Table addDateField(String name){
            addDateField(name, false, false);
            return this;
        }


        public Table enableRecordLog() {
            isRecordLogEnabled = true;
            return this;
        }

        public Table enableSoftDelete() {
            isSoftDeleteEnabled = true;
            return this;
        }

        public Table addIndex(String... columns) {
            indexList.add(new Index(name,null,columns));
            return this;
        }

        public Table addUnique(String... columns) {
            uniqueList.add(new Unique(columns));
            return this;
        }

        public Table addCheck(String conditionalLogic) {
            checkList.add(new Check(conditionalLogic));
            return this;
        }


        public Table addForeignKey(String childColumnName, String parentTableName, Class parentTableClass, String parentColumnName, String onUpdateAction, String onDeleteAction){
            foreignKeyList.add(new ForeignKey(childColumnName, parentTableName, parentTableClass, parentColumnName, onUpdateAction, onDeleteAction));
            return this;
        }

        public List<Index> getIndexList() {
            return indexList;
        }

        public List<Unique> getUniqueList() {
            return uniqueList;
        }

        public List<Check> getCheckList() {
            return checkList;
        }

        public List<ForeignKey> getForeignKeyList() {
            return foreignKeyList;
        }
    }

    public static class Index{
        private String tableName;
        private List<String> columnList = new ArrayList<>();
        public Index(String tableName, Class clazz, String... columns){
            if(TextUtils.isEmpty(tableName)){
                tableName = clazz.getSimpleName();
            }
            this.tableName = tableName;
            columnList.addAll(Arrays.asList(columns));
        }

        public String getTableName() {
            return tableName;
        }

        public List<String> getColumnList() {
            return columnList;
        }
    }


    public static class Unique{
        private List<String> columnList = new ArrayList<>();
        public Unique(String... columns){
            columnList.addAll(Arrays.asList(columns));
        }
        public List<String> getColumnList() {
            return columnList;
        }
    }


    public static class Check{
        private String conditionalLogic;
        public Check(String conditionalLogic){
            this.conditionalLogic = conditionalLogic;
        }

        public String getConditionalLogic() {
            return conditionalLogic;
        }
    }

    public static class ForeignKey{
        public static final String SET_NULL = "SET NULL";
        public static final String SET_DEFAULT = "SET DEFAULT";
        public static final String RESTRICT = "RESTRICT";
        public static final String NO_ACTION = "NO ACTION";
        public static final String CASCADE = "CASCADE";

        private String childColumnName;
        private String parentTableName;
        private Class parentTableClass;
        private String parentColumnName;
        private String onUpdateAction;
        private String onDeleteAction;

        public ForeignKey(String childColumnName, String parentTableName, Class parentTableClass, String parentColumnName, String onUpdateAction, String onDeleteAction) {
            this.childColumnName = childColumnName;
            this.parentTableName = parentTableName;
            this.parentTableClass = parentTableClass;

            if(TextUtils.isEmpty(parentTableName)){
                this.parentTableName = parentTableClass.getSimpleName();
            }

            this.parentColumnName = parentColumnName;
            this.onUpdateAction = onUpdateAction;
            this.onDeleteAction = onDeleteAction;
        }

        public String getChildColumnName() {
            return childColumnName;
        }

        public String getParentTableName() {
            return parentTableName;
        }

        public String getParentColumnName() {
            return parentColumnName;
        }

        public Class getParentTableClass() {
            return parentTableClass;
        }

        public String getOnUpdateAction() {
            return onUpdateAction;
        }

        public String getOnDeleteAction() {
            return onDeleteAction;
        }
    }


    public void addIndex(Index index){
        indexList.add(index);
    }


    public static class Field{
        public static final String TEXT = "TEXT";
        public static final String INTEGER = "INTEGER";
        public static final String REAL = "REAL";
        public static final String BLOB = "BLOB";

        private String name;
        private String type;
        private Class trueType;
        private boolean isNotNull = false;
        private boolean isUnique = false;

        public Field(String name, String type, Class trueType) {
            this.name = name;
            this.type = type;
            this.trueType = trueType;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Class getTrueType() {
            return trueType;
        }

        public void setTrueType(Class trueType) {
            this.trueType = trueType;
        }

        public boolean isNotNull() {
            return isNotNull;
        }

        public void setNotNull(){
            isNotNull = true;
        }

        public void setNullable(){
            isNotNull = false;
        }

        public boolean isUnique() {
            return isUnique;
        }

        public void setUnique(boolean unique) {
            isUnique = unique;
        }
    }



    private static class Builder {
        private String databaseName;
        private int databaseVersion;
        private Map<String, Table> tableMap = new HashMap<>();
        private List<Index> indexList = new ArrayList<>();

        public Builder setDatabaseName(String databaseName) {
            this.databaseName = databaseName;
            return this;
        }

        public Builder setDatabaseVersion(int version) {
            this.databaseVersion = version;
            return this;
        }

        public SQLiteWrapper create(Context context) {
            return new SQLiteWrapper(context, databaseName, databaseVersion, tableMap, indexList);
        }
    }


    public static class TableClass {
        public Long _id = null;
        public Date _created_at = null;
        public Date _updated_at = null;

        public TableClass(){
        }

        protected String getTableName(){
            return this.getClass().getSimpleName();
        }

        protected String getDatabaseName(){
            return null;
        }

        protected void setObjectData(List<Object> dataList){}
        protected void getObjectData(List<Object> dataList){}

        public boolean saveIn(String databaseName){
             return SQLiteWrapper.of(databaseName).save(this);
        }

        private void checkCondition(){
            if(TextUtils.isEmpty(getDatabaseName())) {
                throw new RuntimeException(String.format(
                        "Class %s must define database name in method getDatabaseName() or use method saveIn() instead of save()",
                        getClass().getSimpleName()));
            }
        }

        public boolean save(){
            checkCondition();

            return SQLiteWrapper.of(getDatabaseName()).save(this);
        }

        public boolean updateIn(String databaseName){
            return SQLiteWrapper.of(databaseName).update(this);
        }

        public boolean update(){
            checkCondition();

            return SQLiteWrapper.of(getDatabaseName()).update(this);
        }

        public boolean deleteIn(String databaseName){
            return SQLiteWrapper.of(databaseName).delete(this);
        }

        public boolean delete(){
            checkCondition();

            return SQLiteWrapper.of(getDatabaseName()).delete(this);
        }

        public static <T extends TableClass> T findById(String databaseName, String tableName, Class<T> clazz, long id){
            if(TextUtils.isEmpty(tableName)){
                tableName = clazz.getSimpleName();
            }
            return SQLiteWrapper.of(databaseName).findById(id, tableName, clazz);
        }

        public static <T extends TableClass> List<T> findAll(String databaseName, String tableName, Class<T> clazz){
            if(TextUtils.isEmpty(tableName)){
                tableName = clazz.getSimpleName();
            }
            return SQLiteWrapper.of(databaseName).findAll(tableName, clazz);
        }

        public static <T extends TableClass> List<T> findWithCriteria(String databaseName, String tableName,
                                                                  Class<T> clazz, String whereClause, String[] whereClauseArgs){
            if(TextUtils.isEmpty(tableName)){
                tableName = clazz.getSimpleName();
            }
            return SQLiteWrapper.of(databaseName).findAllWithCriteria(tableName, clazz, whereClause, whereClauseArgs);
        }

        public static <T extends TableClass> List<T> selectQuery(String databaseName, boolean distinct, String tableName,
                                                       Class<T> clazz,
                                                       String[] columns,
                                                       String selection, String[] selectionArgs, String groupBy,
                                                       String having, String orderBy, String limit){
            if(TextUtils.isEmpty(tableName)){
                tableName = clazz.getSimpleName();
            }
            return SQLiteWrapper.of(databaseName).selectQuery(distinct, tableName, clazz, columns,
                    selection, selectionArgs, groupBy, having, orderBy, limit);
        }

        public static <T extends TableClass> List<T> rawSelectQuery(String databaseName,
                                                                    Class<T> clazz, String selectSql, String[] sqlArgs){
            return SQLiteWrapper.of(databaseName).rawSelectQuery(clazz, selectSql, sqlArgs);
        }

        public static void execSQL(String databaseName, String sql, String[] sqlArgs){
            SQLiteWrapper.of(databaseName).execSQL(sql, sqlArgs);
        }

        public static <T extends TableClass> void deleteAll(String databaseName, String tableName, Class<T> clazz){
            SQLiteWrapper.of(databaseName).deleteAll(tableName, clazz);
        }

    }


    public void setMigrationPlan(MigrationPlan migrationPlan){
        this.migrationPlan = migrationPlan;
    }
    private MigrationPlan migrationPlan;

    public interface MigrationStep {
        List<String> getSQLScriptList(SQLiteWrapper sqLiteWrapper);
    }

    public static class RenameTableMigrationStep implements MigrationStep{
        private String oldTableName;
        private String newTableName;

        public RenameTableMigrationStep(String oldTableName, String newTableName){
            this.oldTableName = oldTableName;
            this.newTableName = newTableName;
        }

        @Override
        public List<String> getSQLScriptList(SQLiteWrapper sqLiteWrapper) {
            List<String> sqlScriptList = new ArrayList<>();
            sqlScriptList.add(String.format("ALTER TABLE %s RENAME TO %s", oldTableName, newTableName));
            return sqlScriptList;
        }
    }


    public static class AddTableMigrationStep implements MigrationStep{
        private String tableName;
        public AddTableMigrationStep(String tableName){
            this.tableName = tableName;
        }

        @Override
        public List<String> getSQLScriptList(SQLiteWrapper sqLiteWrapper) {
            List<String> sqlScriptList = new ArrayList<>();
            Table table = sqLiteWrapper.tableMap.get(tableName);
            sqlScriptList.add(sqLiteWrapper.getCreateTableScript(table));
            return sqlScriptList;
        }
    }


    public static class RemoveTableMigrationStep implements MigrationStep{
        private String tableName;
        public RemoveTableMigrationStep(String tableName){
            this.tableName = tableName;
        }

        @Override
        public List<String> getSQLScriptList(SQLiteWrapper sqLiteWrapper) {
            List<String> sqlScriptList = new ArrayList<>();
            sqlScriptList.add(String.format("DROP TABLE %s", tableName));
            return sqlScriptList;
        }
    }


    public interface MigrationPlan {
        List<MigrationStep> getUpgradePlan(int oldVersion, int newVersion);
        List<MigrationStep> getDowngradePlan(int oldVersion, int newVersion);
    }



    private List<Table> readAndParseCreateTableScript(String fileName) {
        List<Table> tableList = new ArrayList<>();

        if (TextUtils.isEmpty(fileName)) {
            Log.d(TAG, "Create SQL script file name is empty");
            return null;
        }

        Log.d(TAG, "Create Script found. Executing...");
        BufferedReader reader = null;

        try {
            InputStream is = assetManager.open(SQLW_FOLDER+fileName);
            InputStreamReader isr = new InputStreamReader(is);
            reader = new BufferedReader(isr);

            String line;
            StringBuilder statement = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                statement.append(line);
                statement.append("\n");

                if (line.endsWith(";")) {
                    String singleScript = statement.toString();

                    singleScript = singleScript
                            .replace("CREATE TABLE", "create table")
                            .replace("PRIMARY KEY", "primary key")
                            .replace("DEFAULT", "default");

                    String tableName = substringBetween("create table","(", singleScript);
                    String fieldsString = substringBetween("(", ")", singleScript);
                    String[] fieldArray = fieldsString.split(",");

                    Table table = new Table(tableName);

                    for (String fieldString : fieldArray){
                        if(fieldString.contains("primary key"))continue;

                        String[] fieldParts = fieldString.split(" ");

                        switch (fieldParts[1]) {
                            case Field.INTEGER:
                                if(fieldString.contains("default 1") || fieldString.contains("default 0")){
                                    //case boolean
                                    table.addBooleanField(fieldParts[0]);
                                } else {
                                    table.addIntField(fieldParts[0]);
                                }
                                break;
                            case Field.TEXT:
                                table.addStringField(fieldParts[0]);
                                break;
                            case Field.REAL:
                                table.addDoubleField(fieldParts[0]);
                                break;
                            case Field.BLOB:
                                //
                                break;
                        }
                    }



                    tableList.add(table);
                    statement = new StringBuilder();
                }
            }

        } catch (IOException e) {
            Log.e(TAG, "Create IOException:", e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(TAG, "Create IOException:", e);
                }
            }
        }

        return tableList;
    }

    private static String substringBetween(String start, String end, String input) {
        int startIndex = input.indexOf(start);
        int endIndex = input.indexOf(end, startIndex + start.length());
        if(startIndex == -1 || endIndex == -1) return input;
        else return input.substring(startIndex + start.length(), endIndex).trim();
    }


    private List<String> readAndGetSQLScript(String fileName) {
        if (TextUtils.isEmpty(fileName)) {
            Log.d(TAG, "Upgrade SQL script file name is empty");
            return null;
        }

        List<String> scriptList = new ArrayList<>();

        Log.d(TAG, "Upgrade Script found. Executing...");
        BufferedReader reader = null;

        try {
            InputStream is = assetManager.open(SQLW_FOLDER+fileName);
            InputStreamReader isr = new InputStreamReader(is);
            reader = new BufferedReader(isr);

            List<String> resultList = getSQLScript(reader);

            if(resultList != null && resultList.size() > 0) {
                scriptList.addAll(resultList);
            }

        } catch (IOException e) {
            Log.e(TAG, "Upgrade IOException:", e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(TAG, "Upgrade IOException:", e);
                }
            }
        }

        return scriptList;
    }

    private List<String> getSQLScript(BufferedReader reader) throws IOException {
        String line;
        StringBuilder statement = new StringBuilder();

        List<String> scriptList = new ArrayList<>();

        while ((line = reader.readLine()) != null) {
            statement.append(line);
            statement.append(" ");
            if (line.endsWith(";")) {
                scriptList.add(statement.toString());
                statement = new StringBuilder();
            }
        }

        return scriptList;
    }

    private void executeSQLScript(SQLiteDatabase db, BufferedReader reader) throws IOException {
        String line;
        StringBuilder statement = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            statement.append(line);
            statement.append("\n");
            if (line.endsWith(";")) {
                db.execSQL(statement.toString());
                statement = new StringBuilder();
            }
        }
    }


    private static String LOOKUP_DATABASE_NAME = "sqlwlookup.db";
    private static void enableLookupDatabase(final Context context){
        addDatabase(new Database() {
            @Override
            public Context getContext() {
                return context;
            }

            @Override
            public String getDatabaseName() {
                return LOOKUP_DATABASE_NAME;
            }

            @Override
            public int getDatabaseVersion() {
                return 1;
            }

            @Override
            public void configure(SQLiteWrapper sqLiteWrapper) {
                sqLiteWrapper.addTable(new Table(TLookup.class)
                        .addStringField("key")
                        .addStringField("string")
                        .addBooleanField("boolean")
                        .addIntField("integer")
                        .addLongField("long")
                        .addFloatField("float")
                        .addDoubleField("double"));
            }
        });
    }


    public static SQLiteWrapper getLookupDatabase(Context context){
        SQLiteWrapper sqLiteWrapper = SQLiteWrapper.of(LOOKUP_DATABASE_NAME);
        if(sqLiteWrapper == null){
            SQLiteWrapper.enableLookupDatabase(context);
            sqLiteWrapper = SQLiteWrapper.of(LOOKUP_DATABASE_NAME);
        }
        return sqLiteWrapper;
    }

    public static class TLookup extends TableClass{
        private String key;
        private String string;
        private boolean aBoolean;
        private int anInt;
        private long aLong;
        private float aFloat;
        private double aDouble;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getString() {
            return string;
        }

        public void setString(String string) {
            this.string = string;
        }

        public boolean getBoolean() {
            return aBoolean;
        }

        public void setBoolean(boolean aBoolean) {
            this.aBoolean = aBoolean;
        }

        public int getInt() {
            return anInt;
        }

        public void setInt(int anInt) {
            this.anInt = anInt;
        }

        public long getLong() {
            return aLong;
        }

        public void setLong(long aLong) {
            this.aLong = aLong;
        }

        public float getFloat() {
            return aFloat;
        }

        public void setFloat(float aFloat) {
            this.aFloat = aFloat;
        }

        public double getDouble() {
            return aDouble;
        }

        public void setDouble(double aDouble) {
            this.aDouble = aDouble;
        }

        @Override
        protected String getDatabaseName() {
            return LOOKUP_DATABASE_NAME;
        }

        @Override
        protected void getObjectData(List<Object> dataList) {
            dataList.add(key);
            dataList.add(string);
            dataList.add(aBoolean);
            dataList.add(anInt);
            dataList.add(aLong);
            dataList.add(aFloat);
            dataList.add(aDouble);
        }

        @Override
        protected void setObjectData(List<Object> dataList) {
            key = (String) dataList.get(0);
            string = (String) dataList.get(1);
            aBoolean = (boolean) dataList.get(2);
            anInt = (int) dataList.get(3);
            aLong = (long) dataList.get(4);
            aFloat = (float) dataList.get(5);
            aDouble = (double) dataList.get(6);
        }
    }
}
