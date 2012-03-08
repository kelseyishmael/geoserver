package org.geoserver.script.py;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.geoserver.script.wps.WpsHandler;
import org.geotools.data.Parameter;
import org.python.core.Py;
import org.python.core.PyList;
import org.python.core.PyObject;
import org.python.core.PyType;

public class PyWpsHandler extends WpsHandler {

    public PyWpsHandler(PythonPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getTitle(ScriptEngine engine) throws ScriptException {
        return str(process(engine).__getattr__("title"));
    }

    @Override
    public String getDescription(ScriptEngine engine) throws ScriptException {
        return str(process(engine).__getattr__("description"));
    }

    @Override
    public Map<String, Parameter<?>> getInputs(ScriptEngine engine)
            throws ScriptException {

        engine.eval("import inspect");
        PyList args = (PyList) engine.eval("inspect.getargspec(run)[0]");
        PyList inputs = (PyList) process(engine).__getattr__("inputs");

        if (args.size() != inputs.size()) {
            throw new RuntimeException(String.format("process function specified %d arguments but"+
                " describes %d inputs", args.size(), inputs.size())); 
        }

        Map<String,Parameter<?>> map = new TreeMap<String, Parameter<?>>();
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i).toString();
            PyObject input = (PyObject) inputs.get(i);
            
            map.put(arg, parameter(arg, input.__getitem__(0), input.__getitem__(1)));
        }
        return map;
    }
    
    @Override
    public Map<String, Parameter<?>> getOutputs(ScriptEngine engine)
            throws ScriptException {
    
        PyList outputs = (PyList) process(engine).__getattr__("outputs");
        Map<String,Parameter<?>> map = new TreeMap<String, Parameter<?>>();
       
        for (int i = 0; i < outputs.size(); i++) {
            PyObject output = (PyObject) outputs.get(i); 

            String name = output.__getitem__(0).toString();
            Object type = output.__getitem__(1);
            Object desc = output.__getitem__(2);

            map.put(name, parameter(name, type, desc));
        }

        return map;
    }

    @Override
    public Map<String, Object> run(Map<String, Object> inputs, ScriptEngine engine) 
            throws ScriptException {
        PyObject run = process(engine);
        
        List<PyObject> args = new ArrayList();
        List<String> kw = new ArrayList();
        
        for (Map.Entry<String,Object> input : inputs.entrySet()) {
            args.add(Py.java2py(input.getValue()));
            kw.add(input.getKey());
        }
        
        PyObject r = run.__call__(args.toArray(new PyObject[args.size()]), 
            kw.toArray(new String[kw.size()]));
        Collection result = null;
      
        if (r instanceof Collection) {
            result = (Collection) r;
        }
        else {
            result = new ArrayList();
            result.add(r);
        }

        Map<String,Parameter<?>> outputs = getOutputs(engine);
        if (result.size() != outputs.size()) {
            throw new IllegalStateException(String.format("Process returned %d values, should have " +
                "returned %d", result.size(), outputs.size()));
        }
        
        Map<String,Object> results = new LinkedHashMap<String, Object>();
        Iterator it = result.iterator();
        for (Parameter output : outputs.values()) {
            results.put(output.key, ((PyObject)it.next()).__tojava__(output.type));
        }
            
        return results;
    }

    PyObject process(ScriptEngine engine) {
        return (PyObject) engine.get("run");
    }

    String str(Object obj) {
        return obj != null ? obj.toString() : null; 
    }

    Parameter parameter(String name, Object type, Object desc) {
        Class clazz = null;
        if (type != null && type instanceof PyType) {
            clazz = PythonPlugin.toJavaClass((PyType)type);
        }
        if (clazz == null) {
            clazz = Object.class;
        }

        desc = desc != null ? desc : name;
        return new Parameter(name, clazz, name, desc.toString());
    }
}