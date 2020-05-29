package com.ahsailabs.sqlwprocessor;

import com.ahsailabs.sqlwannotation.Column;
import com.ahsailabs.sqlwannotation.Index;
import com.ahsailabs.sqlwannotation.Table;
import com.ahsailabs.sqlwannotation.Unique;
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;


@AutoService(Processor.class)
public class SQLWProcessor extends AbstractProcessor {
    private static final String CLASS_SUFFIX = "SQLWHelper";
    private Messager messager;
    private Filer filer;
    private Elements elements;
    private Types types;


    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        messager = processingEnvironment.getMessager();
        filer = processingEnvironment.getFiler();
        elements = processingEnvironment.getElementUtils();
        types = processingEnvironment.getTypeUtils();
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        //get all elements annotated with Table
        Collection<? extends Element> annotatedElements = roundEnvironment.getElementsAnnotatedWith(Table.class);


        //filter out elements we don't need
        List<TypeElement> typeElements = new ImmutableList.Builder<TypeElement>()
                .addAll(ElementFilter.typesIn(annotatedElements))
                .build();

        for (TypeElement type : typeElements) {
            //interfaces are types too, but we only need classes
            //we need to check if the TypeElement is a valid class
            if (isValidClass(type)) {
                generateTableClassHelper(type);
            } else {
                return true;
            }
        }

        // We are the only ones handling Table/Index/Unique/Column annotations
        return true;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotations = new HashSet<>();
        annotations.add(Table.class.getCanonicalName());
        annotations.add(Column.class.getCanonicalName());
        annotations.add(Index.class.getCanonicalName());
        annotations.add(Unique.class.getCanonicalName());

        return annotations;
    }



    private boolean isValidClass(TypeElement type){
        if(type.getKind() != ElementKind.CLASS){
            messager.printMessage(Diagnostic.Kind.ERROR,type.getSimpleName()+" only classes can be annotated with Table");
            return false;
        }

        if(type.getModifiers().contains(Modifier.PRIVATE)){
            messager.printMessage(Diagnostic.Kind.ERROR,type.getSimpleName()+" only public classes can be annotated with Table");
            return false;
        }

        return true;
    }

    private boolean isValidField(VariableElement variableElement){
        if(variableElement.getAnnotation(Column.class)==null){
            return false;
        }

        if(!variableElement.getModifiers().contains(Modifier.PUBLIC)){
            messager.printMessage(Diagnostic.Kind.ERROR,variableElement.getSimpleName()+" only public field can be annotated with Column");
            return false;
        }

        return true;
    }


    private void generateTableClassHelper(TypeElement originatingType){
        String targetClassName = originatingType.getSimpleName().toString();

        Table table = originatingType.getAnnotation(Table.class);
        String tableName = table.name();
        boolean recordLog = table.recordLog();
        boolean softDelete = table.softDelete();



        //create static void method named
        //designTable(SQLiteWrapper sqLiteWrapper)
        //getObjectData(List<Object> dataList, OnlineTryoutItem onlineTryoutItem)
        //setObjectData(List<Object> dataList, OnlineTryoutItem onlineTryoutItem)


        ClassName listClassName = ClassName.get("java.util", "List");
        ClassName objectClassName = ClassName.get("java.lang", "Object");
        ClassName sqliteWrapperClassName = ClassName.get("com.zaitunlabs.zlcore.utils", "SQLiteWrapper");

        TypeVariableName targetTypeVariableName = TypeVariableName.get(targetClassName);
        TypeVariableName sqlwTypeVariableName = TypeVariableName.get("SQLiteWrapper");
        ParameterizedTypeName dataTypeVariableName = ParameterizedTypeName.get(listClassName, objectClassName);


        MethodSpec.Builder designMethodSpecBuilder = MethodSpec.methodBuilder("designTable")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(void.class)
                .addParameter(sqlwTypeVariableName, "sqliteWrapper");

        MethodSpec.Builder getDataSpecBuilder = MethodSpec.methodBuilder("getObjectData")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(void.class)
                .addParameter(dataTypeVariableName, "dataList")
                .addParameter(targetTypeVariableName, "tableClassItem");

        MethodSpec.Builder setDataSpecBuilder = MethodSpec.methodBuilder("setObjectData")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(void.class)
                .addParameter(dataTypeVariableName, "dataList")
                .addParameter(targetTypeVariableName, "tableClassItem");


        List<VariableElement> variableElements = new ImmutableList.Builder<VariableElement>()
                .addAll(ElementFilter.fieldsIn(originatingType.getEnclosedElements()))
                .build();

        designMethodSpecBuilder.addCode("sqliteWrapper.addTable(new $T.Table("+(tableName.isEmpty() ? targetClassName+".class":"\""+tableName+"\"")+")", sqliteWrapperClassName);
        designMethodSpecBuilder.addCode("\n");

        int i=0;
        for (int j=0; j<variableElements.size(); j++){
            VariableElement element = variableElements.get(j);

            if (!isValidField(element))continue;

            Column column = element.getAnnotation(Column.class);

            String fieldName = element.getSimpleName().toString();
            String columnName = column.name();
            if(columnName.isEmpty()){
                columnName = fieldName;
            }

            boolean index = column.index();
            boolean unique = column.unique();
            boolean notNull = column.notNull();



            getDataSpecBuilder.addCode("dataList.add(tableClassItem."+fieldName+");");


            if(element.asType().toString().equals("java.lang.String")){
                designMethodSpecBuilder.addCode(".addStringField(\""+columnName+"\","+(notNull?"true":"false")+","+(unique?"true":"false")+")");
                setDataSpecBuilder.addCode("tableClassItem."+fieldName+" = (String)dataList.get("+i+");");
            } else if(element.asType().toString().equals("int")){
                designMethodSpecBuilder.addCode(".addIntField(\""+columnName+"\","+(notNull?"true":"false")+","+(unique?"true":"false")+")");
                setDataSpecBuilder.addCode("tableClassItem."+fieldName+" = (int)dataList.get("+i+");");
            } else if(element.asType().toString().equals("long")){
                designMethodSpecBuilder.addCode(".addLongField(\""+columnName+"\","+(notNull?"true":"false")+","+(unique?"true":"false")+")");
                setDataSpecBuilder.addCode("tableClassItem."+fieldName+" = (long)dataList.get("+i+");");
            } else if(element.asType().toString().equals("float")){
                designMethodSpecBuilder.addCode(".addFloatField(\""+columnName+"\","+(notNull?"true":"false")+","+(unique?"true":"false")+")");
                setDataSpecBuilder.addCode("tableClassItem."+fieldName+" = (float)dataList.get("+i+");");
            } else if(element.asType().toString().equals("double")){
                designMethodSpecBuilder.addCode(".addDoubleField(\""+columnName+"\","+(notNull?"true":"false")+","+(unique?"true":"false")+")");
                setDataSpecBuilder.addCode("tableClassItem."+fieldName+" = (double)dataList.get("+i+");");
            } else if(element.asType().toString().equals("boolean")){
                designMethodSpecBuilder.addCode(".addBooleanField(\""+columnName+"\","+(notNull?"true":"false")+","+(unique?"true":"false")+")");
                setDataSpecBuilder.addCode("tableClassItem."+fieldName+" = (boolean)dataList.get("+i+");");
            } else if(element.asType().toString().equals("java.util.Date")){
                designMethodSpecBuilder.addCode(".addDateField(\""+columnName+"\","+(notNull?"true":"false")+","+(unique?"true":"false")+")");
                ClassName dateClassName = ClassName.get("java.util", "Date");
                setDataSpecBuilder.addCode("tableClassItem."+fieldName+" = ($T)dataList.get("+i+");", dateClassName);
            }


            designMethodSpecBuilder.addCode("\n");
            setDataSpecBuilder.addCode("\n");
            getDataSpecBuilder.addCode("\n");

            if(index){
                designMethodSpecBuilder.addCode(".addIndex(\""+columnName+"\")");
                designMethodSpecBuilder.addCode("\n");
            }

            i++;
        }


        if(recordLog){
            designMethodSpecBuilder.addCode(".enableRecordLog()");
            designMethodSpecBuilder.addCode("\n");
        }

        if(softDelete){
            designMethodSpecBuilder.addCode(".enableSoftDelete()");
            designMethodSpecBuilder.addCode("\n");
        }

        designMethodSpecBuilder.addCode(");");

        //create a class to wrap our method
        //the class name will be the annotated class name + SQLWH
        TypeSpec targetClass = TypeSpec.classBuilder(originatingType.getSimpleName().toString() + CLASS_SUFFIX)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(designMethodSpecBuilder.build())
                .addMethod(getDataSpecBuilder.build())
                .addMethod(setDataSpecBuilder.build())
                .build();


        //create the file
        JavaFile.Builder javaFileBuilder = JavaFile.builder(originatingType.getEnclosingElement().toString(), targetClass);
        JavaFile javaFile = javaFileBuilder.build();

        try {
            javaFile.writeTo(filer);
        } catch (IOException e) {
            e.printStackTrace();
            messager.printMessage(Diagnostic.Kind.ERROR,e.getMessage());
        }

    }
}
