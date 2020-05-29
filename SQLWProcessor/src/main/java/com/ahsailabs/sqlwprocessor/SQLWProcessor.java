package com.ahsailabs.sqlwprocessor;

import com.ahsailabs.sqlwannotation.Check;
import com.ahsailabs.sqlwannotation.Column;
import com.ahsailabs.sqlwannotation.ForeignKey;
import com.ahsailabs.sqlwannotation.Index;
import com.ahsailabs.sqlwannotation.Table;
import com.ahsailabs.sqlwannotation.Unique;
import com.google.auto.service.AutoService;
import com.google.common.base.CaseFormat;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
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

        return true;
    }


    private void generateTableClassHelper(TypeElement originatingType){
        String targetClassName = originatingType.getSimpleName().toString();
        String targetObjectClassName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL,targetClassName);

        Table table = originatingType.getAnnotation(Table.class);
        String tableName = table.name();
        boolean recordLog = table.recordLog();
        boolean softDelete = table.softDelete();


        //create static void method named
        //designTable(SQLiteWrapper sqLiteWrapper)
        //getObjectData(List<Object> dataList, OnlineTryoutItem onlineTryoutItem)
        //setObjectData(List<Object> dataList, OnlineTryoutItem onlineTryoutItem)


        ClassName listClassName = ClassName.get("java.util", "List");
        ClassName dateClassName = ClassName.get("java.util", "Date");
        ClassName objectClassName = ClassName.get("java.lang", "Object");
        ClassName sqliteWrapperClassName = ClassName.get("com.ahsailabs.sqlitewrapper", "SQLiteWrapper");

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
                .addParameter(targetTypeVariableName, targetObjectClassName);

        MethodSpec.Builder setDataSpecBuilder = MethodSpec.methodBuilder("setObjectData")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(void.class)
                .addParameter(dataTypeVariableName, "dataList")
                .addParameter(targetTypeVariableName, targetObjectClassName);


        List<VariableElement> variableElements = new ImmutableList.Builder<VariableElement>()
                .addAll(ElementFilter.fieldsIn(originatingType.getEnclosedElements()))
                .build();

        designMethodSpecBuilder.addCode("sqliteWrapper.addTable(new $T.Table("+(tableName.isEmpty() ? targetClassName+".class":"\""+tableName+"\"")+")", sqliteWrapperClassName);
        designMethodSpecBuilder.addCode("\n");

        int i=0;
        for (int j=0; j<variableElements.size(); j++){
            VariableElement element = variableElements.get(j);

            if (!isValidField(element))continue;

            boolean isPublicField = element.getModifiers().contains(Modifier.PUBLIC);

            Column column = element.getAnnotation(Column.class);

            String fieldName = element.getSimpleName().toString();
            String columnName = column.name();
            if(columnName.isEmpty()){
                columnName = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE,fieldName);
            }

            boolean index = column.index();
            boolean unique = column.unique();
            boolean notNull = column.notNull();



            String getterSetter = CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL,fieldName);

            if(!isPublicField){
                getDataSpecBuilder.addCode("dataList.add("+targetObjectClassName+".get"+getterSetter+"());");
            } else {
                getDataSpecBuilder.addCode("dataList.add("+targetObjectClassName+"."+fieldName+");");
            }


            String javaType = "";
            ClassName javaClassName = null;
            if(element.asType().toString().equals("java.lang.String")){
                designMethodSpecBuilder.addCode(".addStringField(\""+columnName+"\","+(notNull?"true":"false")+","+(unique?"true":"false")+")");
                javaType = "String";
            } else if(element.asType().toString().equals("int")){
                designMethodSpecBuilder.addCode(".addIntField(\""+columnName+"\","+(notNull?"true":"false")+","+(unique?"true":"false")+")");
                javaType = "int";
            } else if(element.asType().toString().equals("long")){
                designMethodSpecBuilder.addCode(".addLongField(\""+columnName+"\","+(notNull?"true":"false")+","+(unique?"true":"false")+")");
                javaType = "long";
            } else if(element.asType().toString().equals("float")){
                designMethodSpecBuilder.addCode(".addFloatField(\""+columnName+"\","+(notNull?"true":"false")+","+(unique?"true":"false")+")");
                javaType = "float";
            } else if(element.asType().toString().equals("double")){
                designMethodSpecBuilder.addCode(".addDoubleField(\""+columnName+"\","+(notNull?"true":"false")+","+(unique?"true":"false")+")");
                javaType = "double";
            } else if(element.asType().toString().equals("boolean")){
                designMethodSpecBuilder.addCode(".addBooleanField(\""+columnName+"\","+(notNull?"true":"false")+","+(unique?"true":"false")+")");
                javaType = "boolean";
            } else if(element.asType().toString().equals("java.util.Date")){
                designMethodSpecBuilder.addCode(".addDateField(\""+columnName+"\","+(notNull?"true":"false")+","+(unique?"true":"false")+")");
                javaType = "$T";
                javaClassName = dateClassName;
            }

            if(!isPublicField){
                if(javaClassName != null){
                    setDataSpecBuilder.addCode(targetObjectClassName+".set" + getterSetter + "((" + javaType + ")dataList.get(" + i + "));", javaClassName);
                } else {
                    setDataSpecBuilder.addCode(targetObjectClassName+".set" + getterSetter + "((" + javaType + ")dataList.get(" + i + "));");
                }
            } else {
                if(javaClassName != null) {
                    setDataSpecBuilder.addCode(targetObjectClassName+"." + fieldName + " = (" + javaType + ")dataList.get(" + i + ");", javaClassName);
                } else {
                    setDataSpecBuilder.addCode(targetObjectClassName+"." + fieldName + " = (" + javaType + ")dataList.get(" + i + ");");
                }
            }

            designMethodSpecBuilder.addCode("\n");
            setDataSpecBuilder.addCode("\n");
            getDataSpecBuilder.addCode("\n");

            if(index){
                designMethodSpecBuilder.addCode(".addIndex(\""+columnName+"\")");
                designMethodSpecBuilder.addCode("\n");
            }


            ForeignKey foreignKey = element.getAnnotation(ForeignKey.class);
            if(foreignKey != null){
                String parentTableName = foreignKey.parentTableName();
                String parentColumnName = foreignKey.parentColumnName();
                String onUpdate = foreignKey.onUpdate();
                String onDelete = foreignKey.onDelete();

                designMethodSpecBuilder.addCode(".addForeignKey(\""+columnName+"\",\""+parentTableName+"\",null,\""+parentColumnName+"\",\""+onUpdate+"\",\""+onDelete+"\")");
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

        Index index = originatingType.getAnnotation(Index.class);
        Unique unique = originatingType.getAnnotation(Unique.class);
        Check check = originatingType.getAnnotation(Check.class);

        if(index != null){
            String firstIndex = index.first();
            String secondIndex = index.second();
            String thirdIndex = index.third();
            String forthIndex = index.forth();
            String fifthIndex = index.fifth();

            List<String> firstIndexSplit = Splitter.on(",").trimResults().splitToList(firstIndex);
            String xxx = Joiner.on("\",\"").skipNulls().join(firstIndexSplit);
            if(!firstIndex.isEmpty()){
                designMethodSpecBuilder.addCode(".addIndex(\""+xxx+"\")");
            }
            if(!secondIndex.isEmpty()){
                designMethodSpecBuilder.addCode(".addIndex("+secondIndex+")");
            }
            if(!thirdIndex.isEmpty()){
                designMethodSpecBuilder.addCode(".addIndex("+thirdIndex+")");
            }
            if(!forthIndex.isEmpty()){
                designMethodSpecBuilder.addCode(".addIndex("+forthIndex+")");
            }
            if(!fifthIndex.isEmpty()){
                designMethodSpecBuilder.addCode(".addIndex("+fifthIndex+")");
            }
        }

        if(unique != null){
            String firstUnique = unique.first();
            String secondUnique = unique.second();
            String thirdUnique = unique.third();
            String forthUnique = unique.forth();
            String fifthUnique = unique.fifth();


            List<String> firstUniqueSplit = Splitter.on(",").trimResults().splitToList(firstUnique);
            String xxx = Joiner.on("\",\"").skipNulls().join(firstUniqueSplit);
            if(!firstUnique.isEmpty()){
                designMethodSpecBuilder.addCode(".addUnique(\""+xxx+"\")");
            }
            if(!secondUnique.isEmpty()){
                designMethodSpecBuilder.addCode(".addUnique("+secondUnique+")");
            }
            if(!thirdUnique.isEmpty()){
                designMethodSpecBuilder.addCode(".addUnique("+thirdUnique+")");
            }
            if(!forthUnique.isEmpty()){
                designMethodSpecBuilder.addCode(".addUnique("+forthUnique+")");
            }
            if(!fifthUnique.isEmpty()){
                designMethodSpecBuilder.addCode(".addUnique("+fifthUnique+")");
            }
        }

        if(check != null){
            String conditionalLogic = check.conditionalLogic();
            if(!conditionalLogic.isEmpty()){
                designMethodSpecBuilder.addCode(".addCheck(\""+conditionalLogic+"\")");
            }
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
