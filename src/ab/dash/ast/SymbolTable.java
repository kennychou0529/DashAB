package ab.dash.ast;

/***
 * Excerpted from "Language Implementation Patterns",
 * published by The Pragmatic Bookshelf.
 * Copyrights apply to this code. It may not be used to create training material, 
 * courses, books, articles, and the like. Contact us if you are in doubt.
 * We make no guarantees that this code is fit for any purpose. 
 * Visit http://www.pragmaticprogrammer.com/titles/tpdsl for more book information.
***/
import java.util.ArrayList;
import java.util.List;

import org.antlr.runtime.CommonToken;
import org.antlr.runtime.TokenStream;

import ab.dash.DashLexer;

public class SymbolTable {
	private static int ID_COUNTER = 0;
	
	// Scope
    public static final int scGLOBAL = 0;
    public static final int scLOCAL = 1;
    public static final int scMETHOD = 2;
    public static final int scTUPLE = 3;
	
	// Specifiers
    public static final int sCONST = 0;
    public static final int sVAR = 1;
    
    /* tVOID is for procedures with no return and should not be added 
     * to the globals. This is mostly used for debugging type checking.
     */
    public static final int tVOID = 12;		
    public static final BuiltInTypeSymbol _void =
            new BuiltInTypeSymbol("void", tVOID);
    
    // arithmetic types defined in order from narrowest to widest
    public static final int tTUPLE = 0;
    public static final int tBOOLEAN = 1;
    public static final int tCHARACTER = 2;
    public static final int tINTEGER = 3;
    public static final int tREAL = 4;
    public static final int tOUTSTREAM = 5;
    public static final int tINSTREAM = 6;
    public static final int tNULL = 7;
    public static final int tIDENTITY = 8;
    public static final int tINTERVAL = 9;
    public static final int tVECTOR = 10;
    public static final int tMATRIX = 11;
    
    public static final BuiltInTypeSymbol _tuple =
            new BuiltInTypeSymbol("tuple", tTUPLE);
    public static final BuiltInTypeSymbol _boolean =
        new BuiltInTypeSymbol("boolean", tBOOLEAN);
    public static final BuiltInTypeSymbol _character =
        new BuiltInTypeSymbol("character", tCHARACTER);
    public static final BuiltInTypeSymbol _integer =
        new BuiltInTypeSymbol("integer", tINTEGER);
    public static final BuiltInTypeSymbol _real=
        new BuiltInTypeSymbol("real", tREAL);
    public static final BuiltInTypeSymbol _outstream =
            new BuiltInTypeSymbol("std_output()", tOUTSTREAM);
    public static final BuiltInTypeSymbol _instream =
            new BuiltInTypeSymbol("std_input()", tINSTREAM);
    public static final BuiltInTypeSymbol _null =
            new BuiltInTypeSymbol("null", tNULL);
    public static final BuiltInTypeSymbol _identity =
            new BuiltInTypeSymbol("identity", tIDENTITY);
    public static final BuiltInTypeSymbol _interval =
            new BuiltInTypeSymbol("interval", tINTERVAL);
    public static final BuiltInTypeSymbol _vector =
            new BuiltInTypeSymbol("vector", tVECTOR);
    public static final BuiltInTypeSymbol _matrix =
            new BuiltInTypeSymbol("matrix", tMATRIX);
    
    public static final BuiltInSpecifierSymbol _const =
    	new BuiltInSpecifierSymbol("const", sCONST);
    public static final BuiltInSpecifierSymbol _var =
    	new BuiltInSpecifierSymbol("var", sVAR);
    
    // null values
    public String _booleanNull = "false";
    public String _characterNull = "\0";
    public String _integerNull = "0";
    public String _realNull = "0.0";

    public DashListener listener =
        new DashListener() {
    		public void info(String msg) { System.out.println(msg); }
        	public void error(String msg) { System.err.println(msg); }
        };

    /** arithmetic types defined in order from narrowest to widest */
    public static final Type[] indexToType = {
        // 0, 	1,        2,     	  3,    	4,		5,				6,      7,	8,			9,			10,		11
    	_tuple, _boolean, _character, _integer, _real, _outstream, _instream, _null, _identity, _interval, _vector, _matrix
    };
    
    public static final Specifier[] indexToSpecifier = {
        // 0, 	1
        _const, _var
    };

    /** Map t1 op t2 to result type (null implies illegal) */
    public static final Type[][] arithmeticResultType = new Type[][] {
    	/*          	tuple		boolean  	character 	integer 	real		outstream	instream  null       identity	interval	vector		matrix*/
    	/*tuple*/		{null,		null,    	null,   	null,   	null,		null,	null,         null,      null,		null,		null,		null},
        /*boolean*/ 	{null,		null,    	null,   	null,   	null, 		null, 	null,         null,      null,		null,		_vector,	_matrix},
        /*character*/   {null,		null,  		null,    	null,   	null, 		null,	null,         null,      null,		null,		_vector,	_matrix},
        /*integer*/     {null,		null,  		null,    	_integer,   _real,		null,	null,         _integer,  _integer,	_interval,	_vector,	_matrix},
        /*real*/   		{null,		null,  		null,    	_real,   	_real,		null,	null,         _real,     _real,		null,		_vector,	_matrix},
        /*outstream*/   {null,		null,  		null,    	null,   	null, 		null,	null,         null,      null,		null,		null,		null},
        /*instream*/   	{null,		null,  		null,    	null,   	null, 		null,	null,         null,      null,		null,		null,		null},
        /*null*/        {null,      null,       null,       _integer,   _real,  	null,   null,         null,      null,		null,		null,		null},
        /*identity*/    {null,      null,       null,       _integer,   _real,  	null,   null,         null,      null,		null,		null,		null},
        /*interval*/	{null,		null,    	null,   	_interval,   null,		null,	null,         null,      null,		_interval,	_vector,	_matrix},
        /*vector*/		{null,		_vector,    _vector,   	_vector,   	_vector,	null,	null,         null,      null,		_vector,	_vector,	_matrix},
        /*matrix*/		{null,		_matrix,    _matrix,   	_matrix,   	_matrix,	null,	null,         null,      null,		_matrix,	_matrix,	_matrix}
    };
    
    public static final Type[][] logicResultType = new Type[][] {
    	/*          	tuple		boolean  	character 	integer 	real	outstream	instream  null       identity	interval	vector		matrix*/
    	/*tuple*/		{null,		null,    	null,   	null,   	null,	null,	null,         null,      null,		null,		null,		null},
        /*boolean*/ 	{null,		_boolean,   null,   	null,   	null, 	null, 	null,         _boolean,   _boolean,	_vector,	_vector,	_matrix},
        /*character*/   {null,		null,  		null,    	null,   	null, 	null,	null,         null,      null,		null,		null,		null},
        /*integer*/     {null,		null,  		null,    	null,   	null,	null,	null,         null,      null,		null,		null,		null},
        /*real*/   		{null,		null,  		null,    	null,   	null,	null,	null,         null,      null,		null,		null,		null},
        /*outstream*/   {null,		null,  		null,    	null,   	null, 	null,	null,         null,      null,		null,		null,		null},
        /*instream*/   	{null,		null,  		null,    	null,   	null, 	null,	null,         null,      null,		null,		null,		null},
        /*null*/        {null,    	_boolean,   null,       null,       null,   null,   null,         null,      null,		null,		null,		null},
        /*identity*/    {null,    	_boolean,   null,       null,       null,   null,   null,         null,      null,		null,		null,		null},
        /*interval*/	{null,		_vector,  	null,   	null,   	null,	null,	null,         null,      null,		_vector,	_vector,	null},
        /*vector*/		{null,		_vector,   	null,   	null,   	null,	null,	null,         null,      null,		_vector,	_vector,	null},
        /*matrix*/		{null,		_matrix,   	null,   	null,   	null,	null,	null,         null,      null,		null,		null,		_matrix}
    };

    public static final Type[][] relationalResultType = new Type[][] {
    	/*          	tuple		boolean  	character 	integer 	real	outstream	instream	null       identity		interval	vector		matrix*/
    	/*tuple*/		{null,		null,    	null,   	null,   	null,       null,	null,		_boolean,      _boolean,	null,		null,		null},
        /*boolean*/ 	{null,		_boolean,  	null,   	null,   	null,	    null,	null,		_boolean,      _boolean,	null,		_vector,	_matrix},
        /*character*/   {null,		null,  		_boolean,   null,   	null,	    null,	null,		_boolean,      _boolean,	null,		_vector,	_matrix},
        /*integer*/     {null,		null,  		null,    	_boolean,   _boolean,	null,	null,		_boolean,      _boolean,	null,		_vector,	_matrix},
        /*real*/   		{null,		null,  		null,    	_boolean,   _boolean,	null,	null,		_boolean,      _boolean,	null,		_vector,	_matrix},
        /*outstream*/   {null,		null,  		null,    	null,   	null, 	    null,	null,		null,          null,		null,		null,		null},
        /*instream*/   	{null,		null,  		null,    	null,   	null, 	    null,	null,		null,          null,		null,		null,		null},
        /*null*/        {null,   	_boolean,   _boolean,   _boolean,   _boolean,   null,   null,		null,          null,		null,		null,		null},
        /*identity*/    {null,    	_boolean,   _boolean,   _boolean,   _boolean,   null,   null,		null,          null,		null,		null,		null},
        /*interval*/	{null,		null,    	null,   	null,   	null,		null,	null,		null,      null,			_vector,	_vector,	null},
        /*vector*/		{null,		_vector,    _vector,   	_vector,   	_vector,	null,	null,		null,      null,			_vector,	_vector,	null},
        /*matrix*/		{null,		_matrix,    _matrix,   	_matrix,   	_matrix,	null,	null,		null,      null,			null,		null,		_matrix}
    };

    public static final Type[][] equalityResultType = new Type[][] {
    	/*          	tuple		boolean  	character 	integer 	real	outstream		instream 	null       identity		interval	vector		matrix*/
    	/*tuple*/		{_boolean,	null,    	null,   	null,   	null,	    	null,		null,    _boolean,	_boolean,	null,		null,		null},
        /*boolean*/ 	{null,		_boolean,  	null,   	null,   	null,	    	null,		null,    _boolean,	_boolean,	null,		null,		null},
        /*character*/   {null,		null,  		_boolean,   null,   	null,	    	null,		null,    _boolean,	_boolean,	null,		null,		null},
        /*integer*/     {null,		null,  		null,    	_boolean,	_boolean,		null,		null,    _boolean,	_boolean,	null,		null,		null},
        /*real*/   		{null,		null,  		null,    	_boolean,   _boolean,		null,		null,    _boolean,	_boolean,	null,		null,		null},
        /*outstream*/   {null,		null,  		null,    	null,   	null, 	    	null,		null,    null,		null,		null,		null,		null},
        /*instream*/   	{null,		null,  		null,    	null,   	null, 	    	null,		null,    null,		null,		null,		null,		null},
        /*null*/        {_boolean,  _boolean,   _boolean,   _boolean,   _boolean,   	null,   	null,    null,		null,		null,		null,		null},
        /*identity*/    {_boolean,  _boolean,   _boolean,   _boolean,   _boolean,   	null,   	null,    null,		null,		null,		null,		null},
        /*interval*/	{null,		null,    	null,   	null,   	null,			null,		null,    null,      null,		_boolean,	_boolean,	null},
        /*vector*/		{null,		null,    	null,   	null,   	null,			null,		null,    null,      null,		_boolean,	_boolean,	null},
        /*matrix*/		{null,		null,    	null,   	null,   	null,			null,		null,    null,      null,		null,		null,		_boolean}
    };
    
    public static final Type[][] castResultType = new Type[][] {
    	/*          	tuple		boolean  	character 	integer 	real	outstream	instream null       identity	interval	vector	matrix*/
    	/*tuple*/		{_tuple,	null,    	null,   	null,   	null,	null,	null,        null,      null,		null,		null,	null},
        /*boolean*/ 	{null,		_boolean,  _character, _integer,   	_real,	null,	null,        null,      null,		null,		null,	null},
        /*character*/   {null,		_boolean,  _character,	_integer,   _real,	null,	null,        null,      null,		null,		null,	null},
        /*integer*/     {null,		_boolean,  _character, _integer,   	_real,	null,	null,        null,      null,		null,		null,	null},
        /*real*/   		{null,		null,  		null,    	_integer,   _real,	null,	null,        null,      null,		null,		null,	null},
        /*outstream*/   {null,		null,  		null,    	null,   	null, 	null,	null,        null,      null,		null,		null,	null},
        /*instream*/   	{null,		null,  		null,    	null,   	null, 	null,	null,        null,      null,		null,		null,	null},
        /*null*/        {_tuple,    _boolean,  _character,  _integer,   _real,  null,   null,        null,      null,		null,		null,	null},
        /*identity*/    {_tuple,    _boolean,  _character,  _integer,   _real,  null,   null,        null,      null,		null,		null,	null},
        /*interval*/	{null,		null,    	null,   	null,   	null,	null,	null,         null,      null,		null,		null,	null},
        /*vector*/		{null,		null,    	null,   	null,   	null,	null,	null,         null,      null,		null,		null,	null},
        /*matrix*/		{null,		null,    	null,   	null,   	null,	null,	null,         null,      null,		null,		null,	null}
   };

    /** Indicate whether a type needs a promotion to a wider type.
     *  If not null, implies promotion required.  Null does NOT imply
     *  error--it implies no promotion.  This works for
     *  arithmetic, equality, and relational operators in Dash.
     */
    public static final Type[][] promoteFromTo = new Type[][] {
        /*          	tuple		boolean  	character 	integer 	real	outstream	instream   null       identity	interval	vector		matrix*/
    	/*tuple*/		{null,		null,    	null,   	null,   	null,	null,	    null,      null,      null,		null,		null,		null},
        /*boolean*/ 	{null,		null,    	null,   	null,   	null,	null,	    null,      null,      null,		null,		null,		null},
        /*character*/   {null,		null,  		null,    	null,   	null,	null,	    null,      null,      null,		null,		null,		null},
        /*integer*/     {null,		null,  		null,    	null,   	_real,	null,	    null,      null,      null,		null,		null,		null},
        /*real*/   		{null,		null,  		null,    	null,   	null, 	null,       null,      null,      null,		null,		null,		null},
        /*outstream*/   {null,		null,  		null,    	null,   	null, 	null,	    null,      null,      null,		null,		null,		null},
        /*instream*/   	{null,		null,  		null,    	null,   	null, 	null,	    null,      null,      null,		null,		null,		null},
        /*null*/        {_tuple,    _boolean,   _character, _integer,   _real,  null,       null,      null,      null,		_interval,	_vector,	_matrix},
        /*identity*/    {_tuple,    _boolean,   _character, _integer,   _real,  null,       null,      null,      null,		_interval,	_vector,	_matrix},
        /*interval*/	{null,		null,    	null,   	null,   	null,	null,		null,      null,      null,		null,		_vector,	null},
        /*vector*/		{null,		null,    	null,   	null,   	null,	null,		null,      null,      null,		null,		_vector,	null},
        /*matrix*/		{null,		null,    	null,   	null,   	null,	null,		null,      null,      null,		null,		null,		_matrix}
    };    

    

    public static int getID() {
		ID_COUNTER++;
		return ID_COUNTER;
	}
    
    public GlobalScope globals = new GlobalScope();
    public ArrayList<ArrayList<Type>> tuples;
    
    private int error_count;
	private int warnings_count;
    private StringBuffer errorSB;
    private StringBuffer warningSB;

    /** Need to have token buffer to print out expressions, errors */
    TokenStream tokens;

    public SymbolTable(TokenStream tokens) {
        this.tokens = tokens;
        
        this.error_count = 0;
        this.warnings_count = 0;
        this.errorSB = new StringBuffer();
        this.warningSB = new StringBuffer();
        
        this.tuples = new ArrayList<ArrayList<Type>>();
        
        initTypeSystem();
    }

    protected void initTypeSystem() {
        for (Type t : indexToType) {
            if ( t!=null ) globals.define((BuiltInTypeSymbol)t);
        }
        
        for (Specifier s : indexToSpecifier) {
        	if ( s!=null ) globals.define((BuiltInSpecifierSymbol)s);
        }
        
        // Built in
        // stream_state       
        MethodSymbol stream_state =
            	new MethodSymbol("stream_state", _integer, globals);
        
        VariableSymbol stream_state_input = new VariableSymbol("inp", _instream, _const);
        stream_state.define(stream_state_input);
        
        globals.define(stream_state);
        
        // length
        MethodSymbol length =
            	new MethodSymbol("length", _integer, globals);
        
        VariableSymbol length_input = new VariableSymbol("vector", _vector, _const);
        length.define(length_input);
        
        globals.define(length);
        
        // rows
        MethodSymbol rows =
            	new MethodSymbol("rows", _integer, globals);
        
        VariableSymbol rows_input = new VariableSymbol("matrix", _matrix, _const);
        rows.define(rows_input);
        
        globals.define(rows);
        
        // columns
        MethodSymbol columns =
            	new MethodSymbol("columns", _integer, globals);
        
        VariableSymbol columns_input = new VariableSymbol("matrix", _matrix, _const);
        columns.define(columns_input);
        
        globals.define(columns);
        
        // reverse
        MethodSymbol reverse =
            	new MethodSymbol("reverse", _vector, globals);
        
        VariableSymbol reverse_input = new VariableSymbol("vector", _vector, _const);
        reverse.define(reverse_input);
        
        globals.define(reverse);
    }
    
    public int getWarningCount() {
		return this.warnings_count;
	}
	
	public int getErrorCount() {
		return this.error_count;
	}
	
	public String getErrors() { return this.errorSB.toString(); }
	public String getWarnings() { return this.warningSB.toString(); }
	
    private void warning(String msg) {
		this.warnings_count++;
		this.listener.info(msg);
		this.warningSB.append(msg);
	}
	
	public void error(String msg) {
		this.error_count++;
		this.listener.error(msg);
		this.errorSB.append(msg);
	}
	
	public boolean checkIfDefined(DashAST a) {
		if (a.symbol != null && a.symbol.type != null ) {
		    return true;
		}
		
		error("line " + a.getLine() + ": " +
   			 text(a)+ " is not defined in the program.");
        return false;
    }
	
	private static Type getElementType(Type type) {
		if (type.getTypeIndex() == tINTERVAL) {
			return _integer;
		} else if (type.getTypeIndex() == tVECTOR) {
			return ((VectorType)type).elementType;
		} else if (type.getTypeIndex() == tMATRIX) {
			return ((MatrixType)type).elementType;
		}
		
		return type;
	}
	
	private Type getPromoteType(Type from, Type to) {
		Type fType = getElementType(from);
		Type tType = getElementType(to);
		
		int tf = fType.getTypeIndex();
		int tt = tType.getTypeIndex();
		
		Type type = promoteFromTo[tf][tt];
		
		if (type == null)
			return null;
		
		if (from.getTypeIndex() == tINTERVAL) {
			return new VectorType(type, 0);
		} else if (from.getTypeIndex() == tVECTOR) {
			return new VectorType(type, 0);
		} else if (from.getTypeIndex() == tMATRIX) {
			return new MatrixType(type, 0, 0);
		}
		
		return type;
	}
	
	private Type promote(Type from, Type to) {
		int tf = from.getTypeIndex();
		int tt = to.getTypeIndex();
		
		Type type = promoteFromTo[tf][tt];
		
		if (type != null) {
			if (type.getTypeIndex() == tINTERVAL) {
				return getPromoteType(from, to);
			} else if (type.getTypeIndex() == tVECTOR) {
				return getPromoteType(from, to);
			} else if (type.getTypeIndex() == tMATRIX) {
				return getPromoteType(from, to);
			}
		}
		
		return type;
	}

    public Type getResultType(Type[][] typeTable, DashAST a, DashAST b) {
    	 Type aType = a.evalType; // type of left operand
         Type bType = b.evalType; // type of right operand
         
         int ta = aType.getTypeIndex();
         int tb = bType.getTypeIndex();
         if (ta == tNULL || ta == tIDENTITY) {
        	 CommonToken token = null;
        	 if (ta == tNULL)
        		 token = convertFromNull(bType);
        	 else
        		 token = convertFromIdentity(bType);
             if (token != null) {
                 a.evalType = b.evalType;
                 a.token = token;
             }  
         }
         
         if (tb == tNULL || tb == tIDENTITY) { 
        	 CommonToken token = null;
        	 if (tb == tNULL)
        		 token = convertFromNull(aType);
        	 else
        		 token = convertFromIdentity(aType);
             if (token != null) {
                 b.evalType = a.evalType;
                 b.token = token;
             }  
         }
        
        Type result = null;
        Type type = typeTable[ta][tb];    // operation result typenull
        if ( type==null ) {
            error("line " + a.getLine() + ": " +
            		text(a)+", "+
                    text(b)+" have incompatible types in "+
                    text((DashAST)a.getParent()));
        }
        else {
        	if (type.getTypeIndex() == tINTERVAL) {
        		result = new IntervalType(0, 0);
        	} else if (type.getTypeIndex() == tVECTOR || 
        			type.getTypeIndex() == tMATRIX) {
        		
        		Type aElementType = getElementType(aType);
        		Type bElementType  = getElementType(bType);
        		
        		ta = aElementType.getTypeIndex();
        		tb = bElementType.getTypeIndex();
        		
        		Type elementType = typeTable[ta][tb];    // operation result type
        		if ( elementType==null ) {
                    error("line " + a.getLine() + ": " +
                    		text(a)+", "+
                            text(b)+" have incompatible types in "+
                            text((DashAST)a.getParent()));
                }
                else {
                    a.promoteToType = promote(aType, bType);
                    b.promoteToType = promote(bType, aType);
                }
                
        		if (type.getTypeIndex() == tVECTOR)
        			result = new VectorType(elementType, 0);
        		else if (type.getTypeIndex() == tMATRIX)
        			result = new MatrixType(elementType, 0, 0);
        	} else {
        		a.promoteToType = promoteFromTo[ta][tb];
                b.promoteToType = promoteFromTo[tb][ta];
                
        		result = type;
        	}
        }
        return result;
    }

    public Type bop(DashAST a, DashAST b) {
    	return getResultType(arithmeticResultType, a, b);
    }
    
    public Type lop(DashAST a, DashAST b) {
        return getResultType(logicResultType, a, b);
    }
    
    public Type relop(DashAST a, DashAST b) {
    	return getResultType(relationalResultType, a, b);
    }
    
    public Type eqop(DashAST a, DashAST b) {
    	getResultType(equalityResultType, a, b);
        // even if the operands are incompatible, the type of
        // this operation must be boolean
        return _boolean;
    }
    
    public Type range(DashAST a, DashAST b) {
        if (!(a.evalType.getTypeIndex() == tINTEGER ||
        		a.evalType.getTypeIndex() == tNULL ||
        		a.evalType.getTypeIndex() == tIDENTITY)) {
        	error("line " + a.getLine() + ": Left hand side of range needs to evaluate to an integer.");
        	return null;
        }
        
        if (!(b.evalType.getTypeIndex() == tINTEGER ||
        		b.evalType.getTypeIndex() == tNULL ||
        		b.evalType.getTypeIndex() == tIDENTITY)) {
        	error("line " + b.getLine() + ": Right hand side of range needs to evaluate to an integer.");
	    	return null;
	    }
        
        IntervalType type = new IntervalType(0, 0);
        type.def = (DashAST) a.parent;
        type.def.evalType = type;
        
        return type;
    }

    public Type uminus(DashAST a) {
    
        Type at = a.evalType;
        if (at.getTypeIndex() == tINTERVAL) {
        	at = _integer;
        } else if (at.getTypeIndex() == tVECTOR) {
        	at = ((VectorType)at).elementType;
        } else if (at.getTypeIndex() == tMATRIX) {
        	at = ((MatrixType)at).elementType;
        }
        
        if ( !(at.getTypeIndex() == tINTEGER || 
        		at.getTypeIndex() == tREAL ||
        		at.getTypeIndex() == tNULL ||
        		at.getTypeIndex() == tIDENTITY) ) {
            error("line " + a.getLine() + ": " +
            		text(a)+" must have integer or real type in "+
                           text((DashAST)a.getParent()));
            return null;
        }
        return a.evalType;
    }
    
    public Type unot(DashAST a) {
        if (a.evalType  == _null) { 
            CommonToken token = convertFromNull(_boolean);
            a.evalType = _boolean;
            a.token = token;
        }  
        
        if (a.evalType  == _identity) { 
            CommonToken token = convertFromIdentity(_boolean);
            a.evalType = _boolean;
            a.token = token;
        }  
        if ( a.evalType.getTypeIndex() == tVECTOR) {
        	Type e = ((VectorType)a.evalType).elementType;
        	
        	if ( e.getTypeIndex() != tBOOLEAN ) {
                error("line " + a.getLine() + ": " +
                		text(a)+" must have boolean[] type in "+
                               text((DashAST)a.getParent()));
                return _boolean; // even though wrong, assume result boolean
            }
        } else if ( a.evalType.getTypeIndex() == tMATRIX) {
        	Type e = ((MatrixType)a.evalType).elementType;
        	
        	if ( e.getTypeIndex() != tBOOLEAN ) {
                error("line " + a.getLine() + ": " +
                		text(a)+" must have boolean[][] type in "+
                               text((DashAST)a.getParent()));
                return _boolean; // even though wrong, assume result boolean
            }
        } else if ( a.evalType != _boolean ) {
            error("line " + a.getLine() + ": " +
            		text(a)+" must have boolean type in "+
                           text((DashAST)a.getParent()));
            return _boolean; // even though wrong, assume result boolean
        }
        return a.evalType;
    }

    public Type vectorIndex(DashAST id, DashAST index) {
        Type t = id.evalType;
        if ( t.getTypeIndex() != tVECTOR && t.getTypeIndex() != tINTERVAL)
        {
            error("line " + id.getLine() + " : " + 
            		text(id)+" must be an vector variable in "+
                    text((DashAST)id.getParent()));
            return null;
        }
        
        int texpr = index.evalType.getTypeIndex();
        Type element = null;
        
        if ( t.getTypeIndex() == tVECTOR)
        	element = ((VectorType)t).elementType;
        
        if ( t.getTypeIndex() == tINTERVAL)
        	element = _integer;
        
        if (texpr == tINTERVAL) {
        	return new VectorType(element, 0);
        } else if (texpr == tVECTOR) {
        	VectorType vectorType = (VectorType)index.evalType;
        	if (vectorType.elementType.getTypeIndex() == tINTEGER)
        		return new VectorType(element, 0);
        	else {
        		error("line " + index.getLine() + " : " + 
        				text(index)+" indexing vector must be of type integer in "+
                        text((DashAST)index.getParent()));
        		return null;
        	}
        } else if (texpr != tINTEGER) {
        	error("line " + index.getLine() + " : " + 
    				text(index)+" index must be of type integer in "+
                    text((DashAST)index.getParent()));
    		return null;
        }
        
        return element;
    }
    
    public Type matrixIndex(DashAST id, DashAST row, DashAST column) {
        Type t = id.evalType;
        if ( t.getTypeIndex() != tMATRIX )
        {
            error(text(id)+" must be an matrix variable in "+
                           text((DashAST)id.getParent()));
            return null;
        }
        
        Type eType = ((MatrixType)t).elementType;
        
        int trow = row.evalType.getTypeIndex();
        int tcolumn = column.evalType.getTypeIndex();
        
        if (trow == tINTERVAL && tcolumn == tINTERVAL) {
        	return t;
        }
        
        if (trow == tVECTOR && tcolumn == tVECTOR) {
        	VectorType rowType = (VectorType)row.evalType;
        	VectorType columnType = (VectorType)column.evalType;
        	if (rowType.elementType.getTypeIndex() == tINTEGER &&
        			columnType.elementType.getTypeIndex() == tINTEGER)
        		return t;
        	else {
        		error("line " + row.getLine() + " : " + 
        				text(row)+" indexing vector must be of type integer in "+
                        text((DashAST)row.getParent()));
        		return null;
        	}
        }
        
        if (trow == tINTERVAL && tcolumn == tINTEGER) {
        	return new VectorType(eType, 0);
        }
        
        if (trow == tVECTOR && tcolumn == tINTEGER) {
        	VectorType rowType = (VectorType)row.evalType;
        	if (rowType.elementType.getTypeIndex() == tINTEGER)
        		return new VectorType(eType, 0);
        	else {
        		error("line " + row.getLine() + " : " + 
        				text(row)+" indexing vector for row must be of type integer in "+
                        text((DashAST)row.getParent()));
        		return null;
        	}
        }
        
        if (tcolumn == tVECTOR && trow == tINTERVAL) {
        	VectorType columnType = (VectorType)column.evalType;
        	if (columnType.elementType.getTypeIndex() == tINTEGER)
        		return t;
        	else {
        		error("line " + row.getLine() + " : " + 
        				text(row)+" indexing vector for column must be of type integer in "+
                        text((DashAST)row.getParent()));
        		return null;
        	}
        }
        
        if (trow == tVECTOR && tcolumn == tINTERVAL) {
        	VectorType rowType = (VectorType)row.evalType;
        	if (rowType.elementType.getTypeIndex() == tINTEGER)
        		return t;
        	else {
        		error("line " + row.getLine() + " : " + 
        				text(row)+" indexing vector for row must be of type integer in "+
                        text((DashAST)row.getParent()));
        		return null;
        	}
        }
        
        if (tcolumn == tVECTOR && trow == tINTEGER) {
        	VectorType columnType = (VectorType)column.evalType;
        	if (columnType.elementType.getTypeIndex() == tINTEGER)
        		return new VectorType(eType, 0);
        	else {
        		error("line " + row.getLine() + " : " + 
        				text(row)+" indexing vector for column must be of type integer in "+
                        text((DashAST)row.getParent()));
        		return null;
        	}
        }
        
        if (tcolumn == tINTERVAL && trow == tINTEGER) {
        	return new VectorType(eType, 0);
        }
        
        if (trow != tINTEGER || tcolumn != tINTEGER) {
        	error("line " + row.getLine() + " : " + 
    				text(row)+", "+text(column)+" index must be of type integer in "+
                    text((DashAST)row.getParent()));
        	error("R: " + trow);
        	error("C: " + trow);
        	 return null;
        }
        
        return eType;
    }

    public Type call(DashAST id, List<?> args) {
        Symbol s = id.scope.resolve(id.getText());
        if ( s.getClass() != MethodSymbol.class ) {
            error(text(id)+" must be a function or procedure in "+
                           text((DashAST)id.getParent()));
            return null;
        }
		
        MethodSymbol ms = (MethodSymbol)s;
        id.symbol = ms;
        int i=0;
        
        // Built in functions
        if (ms.name.equals("length")) {
        	if (args.size() != 1) {
        		error("line " + id.getLine() + ": length takes one vector.");
        		return null;
        	}
        	DashAST argAST = (DashAST)args.get(0);
        	if (argAST.evalType.getTypeIndex() != tINTERVAL &&
        			argAST.evalType.getTypeIndex() != tVECTOR ) {
        		error("line " + id.getLine() + ": length takes one vector.");
        		return null;
        	}
        	
        	return ms.type;
        }
        
        if (ms.name.equals("rows")) {
        	if (args.size() != 1) {
        		error("line " + id.getLine() + ": rows takes one matrix.");
        		return null;
        	}
        	DashAST argAST = (DashAST)args.get(0);
        	if (argAST.evalType.getTypeIndex() != tMATRIX) {
        		error("line " + id.getLine() + ": rows takes one matrix.");
        		return null;
        	}
        	
        	return ms.type;
        }
        
        if (ms.name.equals("columns")) {
        	if (args.size() != 1) {
        		error("line " + id.getLine() + ": columns takes one matrix.");
        		return null;
        	}
        	DashAST argAST = (DashAST)args.get(0);
        	if (argAST.evalType.getTypeIndex() != tMATRIX) {
        		error("line " + id.getLine() + ": columns takes one matrix.");
        		return null;
        	}
        	
        	return ms.type;
        }
        
        if (ms.name.equals("reverse")) {
        	if (args.size() != 1) {
        		error("line " + id.getLine() + ": reverse takes one vector.");
        		return null;
        	}
        	DashAST argAST = (DashAST)args.get(0);
        	if (argAST.evalType.getTypeIndex() != tINTERVAL &&
        			argAST.evalType.getTypeIndex() != tVECTOR) {
        		error("line " + id.getLine() + ": reverse takes one vector.");
        		return null;
        	}
        	
        	Type type = _integer;
        	if (argAST.evalType.getTypeIndex() == tVECTOR) {
        		VectorType vt = (VectorType) argAST.evalType;
        		type = vt.elementType;
        	}
        	
        	return new VectorType(type, 0);
        }
		
        for (Symbol a : ms.orderedArgs.values() ) { // for each arg
            DashAST argAST = null;
            try {
                argAST = (DashAST)args.get(i++);
            }
            catch (IndexOutOfBoundsException e) {
                error("line " + id.getLine() + ": invalid number of args to '" + id.getText() + "'");
                break;
            }

            // get argument expression type and expected type
            Type actualArgType = argAST.evalType;
            Type formalArgType = ((VariableSymbol)a).type;
			
            // do we need to promote argument type to defined type?
            argAST.promoteToType = promote(actualArgType, formalArgType);
            if ( !canAssignTo(actualArgType, formalArgType,
                              argAST.promoteToType) ) {
                error("line " + id.getLine() + ": argument "+
                               a.name+":<"+a.type+"> of "+ms.name+
                               "() is incompatible type");
            }
        }
        
        return ms.type;
    }

    public Type member(DashAST id, DashAST field) {
    	Type type = null;
    	if (id.getToken().getType() == DashLexer.ID) {
			VariableSymbol st = (VariableSymbol)id.scope.resolve(id.getText());
	        id.symbol = st;
	        type = st.type;
    	} else {
    		type = id.evalType;
    	}
    	
        if ( type.getTypeIndex() != tTUPLE ) {
            error("line " + id.getLine() + ": " +
            		text(id)+" must have tuple type in "+
                           text((DashAST)id.getParent()));
            return null;
        }
        
        TupleTypeSymbol scope = (TupleTypeSymbol) type;
        Symbol s = scope.resolveMember(field.getText());	// resolve ID in scope
        field.symbol = s;
        
        if (s == null) {
        	 error("line " + id.getLine() + ": " +
             		text(id)+" tuple member not found "+
                            text((DashAST)id.getParent()));
        	return null;
        }
        
        return s.type;           // return ID's type
    }
    
    public void ret(MethodSymbol ms, DashAST expr) {
        Type retType = ms.type; // promote return expr to function decl type?
        Type exprType = expr.evalType;
        
        expr.promoteToType = promote(exprType, retType);
        if ( !canAssignTo(exprType, retType, expr.promoteToType) ) {
            error("line " + expr.getLine() + ": " +
            		text(expr)+", "+
            		ms.name+"():<"+ms.type+"> have incompatible types in "+
            		text((DashAST)expr.getParent()));
        }
    }

    // assignnment stuff (arg assignment in call())
    
    public void declinit(DashAST declID, DashAST init) {
        int te = init.evalType.getTypeIndex(); // promote expr to decl type?
        
        // Check for Type Inference
        if (declID.symbol.type == null) {
        	if (te != tNULL && te != tIDENTITY) {
        		declID.symbol.type = init.evalType;
        	} else {
        		error("line " + declID.getLine() + ": type cannot be inferred for " + text((DashAST) init.getParent()));
        		return;
        	}
        } else {
        	if (declID.symbol.type.getTypeIndex() == tVECTOR) {
        		VectorType vType = (VectorType) declID.symbol.type;
        		DashAST vType_node = vType.def;
        		DashAST size = (DashAST) vType_node.getChild(1);
        		
        		if (size.token.getType() == DashLexer.INFERRED &&
        				(init.evalType.getTypeIndex() != tINTERVAL &&
        				init.evalType.getTypeIndex() != tVECTOR)) {
        			error("line " + declID.getLine() + ": cannot use scalar or none-vector type to instantiate an un-sized vector " + text((DashAST) init.getParent()));
        			return;
        		}
        	}
        	
        	if (declID.symbol.type.getTypeIndex() == tMATRIX) {
        		MatrixType matType = (MatrixType) declID.symbol.type;
        		DashAST matType_node = matType.def;
        		DashAST rows = (DashAST) matType_node.getChild(1);
        		DashAST columns = (DashAST) matType_node.getChild(2);
        		
        		if ((rows.token.getType() == DashLexer.INFERRED ||
        				columns.token.getType() == DashLexer.INFERRED) &&
        				init.evalType.getTypeIndex() != tMATRIX) {
        			error("line " + declID.getLine() + ": cannot use scalar or none-matrix type to instantiate an un-sized matrix " + text((DashAST) init.getParent()));
        			return;
        		}
        	}
        }
        
        if (declID.symbol.type instanceof TypedefSymbol) {
        	TypedefSymbol typedef = (TypedefSymbol) declID.symbol.type;
        	declID.symbol.type = typedef.def_type;
        }
        
        if (declID.symbol.type.getTypeIndex() == tTUPLE) {
        	if (te == tNULL || te == tIDENTITY) {
        		DashAST parent = (DashAST) init.getParent();
        		int index = init.getChildIndex();
        		
        		DashAST expr = new DashAST(new CommonToken(DashLexer.EXPR, "EXPR"));
        		DashAST tuple_list = new DashAST(new CommonToken(DashLexer.TUPLE_LIST, "TUPLE_LIST"));
        		
        		TupleTypeSymbol tuple = (TupleTypeSymbol) declID.symbol.type;
        		ArrayList<Symbol> fields = tuple.fields;
        		
        		for (int i = 0; i < fields.size(); i++) {
        			VariableSymbol field = (VariableSymbol) fields.get(i);
        			Type type = field.type;
        			
        			DashAST expr_t = new DashAST(new CommonToken(DashLexer.EXPR, "EXPR"));
        			DashAST expr_e = null;
        			
        			if (te == tNULL)
        				expr_e = new DashAST(convertFromNull(type));
        			else if (te == tIDENTITY)
        				expr_e = new DashAST(convertFromIdentity(type));
        			
        			expr_e.evalType = type;
        			expr_t.evalType = type;
        			
        			expr_t.addChild(expr_e);
        			tuple_list.addChild(expr_t);
        		}
        		
        		tuple_list.evalType = tuple;
        		expr.evalType = tuple;
        		init = expr;
        		te = tTUPLE;
        		
        		expr.addChild(tuple_list);
        		parent.replaceChildren(index, index, expr);

        	}
        } 
        
        
		Type tdecl = declID.symbol.type;
		declID.evalType = declID.symbol.type;
		
		init.promoteToType = promote(init.evalType, tdecl);
		if (!canAssignTo(init.evalType, tdecl, init.promoteToType)) {
			error("line " + declID.getLine() + ": " + declID.evalType + " "
					+ declID.getText() + " is incompatible with "
					+ init.evalType + " type");
		}
    }
    
    private boolean isConstantVariable(DashAST t) {
    	switch (t.token.getType()) {
    	case DashLexer.ID: {
    		if (t.symbol != null) {
    			if (t.symbol instanceof VariableSymbol) {
    				VariableSymbol vs = (VariableSymbol)t.symbol;
    				return vs.specifier.getSpecifierIndex() == sCONST;
    			}
    		}
    	}
    	case DashLexer.DOT: {
    		DashAST id = (DashAST) t.getChild(0);
    		DashAST field = (DashAST) t.getChild(1);
    		if (id.getToken().getType() == DashLexer.ID) {
    			VariableSymbol st = (VariableSymbol)id.scope.resolve(id.getText());
    			if (st.specifier.getSpecifierIndex() == sCONST) {
    				return true;
    			}
    	        
    			TupleTypeSymbol scope = (TupleTypeSymbol) st.type;
    			VariableSymbol s = (VariableSymbol)scope.resolveMember(field.getText());
    			
    			if (s == null)
    				return true;
    			
    			return s.specifier.getSpecifierIndex() == sCONST;
        	}
    	}
    	case DashLexer.VECTOR_INDEX: {
    		DashAST id = (DashAST) t.getChild(0);
    		if (id.getToken().getType() == DashLexer.ID) {
    			VariableSymbol st = (VariableSymbol)id.scope.resolve(id.getText());
    			if (st.specifier.getSpecifierIndex() == sCONST) {
    				return true;
    			}
    			
    			return false;
        	}
    	}
    	case DashLexer.MATRIX_INDEX: {
    		DashAST id = (DashAST) t.getChild(0);
    		if (id.getToken().getType() == DashLexer.ID) {
    			VariableSymbol st = (VariableSymbol)id.scope.resolve(id.getText());
    			if (st.specifier.getSpecifierIndex() == sCONST) {
    				return true;
    			}
    			
    			return false;
        	}
    	}
    	case DashLexer.EXPR:
    		break;
    		
    	default:
    		return true;
    	}
    	
    	for (int i = 0; i < t.getChildCount(); i++) {
    		if (isConstantVariable((DashAST) t.getChild(i)))
    			return true;
    	}
    	
    	return false;
    }

    public void assign(DashAST lhs, DashAST rhs) {
    	if (isConstantVariable(lhs)) {
			error("line " + lhs.getLine() + ": " +
        			"The specifier for " + text(lhs) + 
        			" is constant and can not be reassigned in "+
                    text((DashAST)lhs.getParent()));
			return;
		}
    	
        rhs.promoteToType = promote(rhs.evalType, lhs.evalType);
        if ( !canAssignTo(rhs.evalType, lhs.evalType, rhs.promoteToType) ) {
            error("line " + lhs.getLine() + ": " +
            			text(lhs)+", "+
                        text(rhs)+" have incompatible types in "+
                        text((DashAST)lhs.getParent()));
        }
    }
    
    public void checkOutput(DashAST print) {
    	if (print.getChildCount() != 2) {
    		 error("line " + print.getLine() + ": " +
    				 " not enough arguments in "+
                     text(print));
    		 return;
    	}
    	
    	DashAST stream = (DashAST) print.getChild(0);
    	DashAST lhs = (DashAST) print.getChild(1);

    	VariableSymbol s = (VariableSymbol)stream.scope.resolve(stream.getText());
    	stream.symbol = s;
    	
    	boolean valid = false;
    	if (lhs.evalType != null) {
	        //String s = super.toString();
	        if ( lhs.evalType.getTypeIndex() == tBOOLEAN ||
	                lhs.evalType.getTypeIndex() == tCHARACTER || 
	                lhs.evalType.getTypeIndex() == tINTEGER ||
	                lhs.evalType.getTypeIndex() == tREAL ||
	                lhs.evalType.getTypeIndex() == tINTERVAL ||
	                lhs.evalType.getTypeIndex() == tVECTOR ||
	                lhs.evalType.getTypeIndex() == tMATRIX) {
	        	valid = true;
	        }
    	}
    	
    	if (!valid)
    		 error("line " + print.getLine() + ": invalid type " + lhs.evalType + " sent to outstream");
        
    	if (s.type != null) {
    		if (s.type.getTypeIndex() != SymbolTable.tOUTSTREAM) {
    			error("line " + print.getLine() + ": " +
       				 " the ouput stream is not a valid stream in "+
                     text(print) + ", the stream needst to be of "+
       				 "type std_output(). Currently, it is type " + s);
       		 	return;
    		}
    	} else {
    		error("line " + print.getLine() + ": " +
    				"the ouput stream is currently undefined in "+
                    text(print));
    		return;
    	}
    	
    	// Remove output stream since it is not needed after this point
    	print.deleteChild(0);
    }

    public void checkInput(DashAST input) {
    	if (input.getChildCount() != 2) {
			error("line " + input.getLine() + ": "
					+ " not enough arguments in " + text(input));
			return;
		}

		DashAST stream = (DashAST) input.getChild(0);
		DashAST lhs = (DashAST) input.getChild(1);

		VariableSymbol s = (VariableSymbol) stream.scope.resolve(stream
				.getText());
		stream.symbol = s;
		
		if ( lhs.evalType != _boolean &&
                lhs.evalType != _integer && 
                lhs.evalType != _real && 
                lhs.evalType!= _character) {
            error("line " + input.getLine() + ": invalid type " + lhs.evalType + " sent to instream");
        }

		if (s.type != null) {
			if (s.type.getTypeIndex() != SymbolTable.tINSTREAM) {
				error("line " + input.getLine() + ": "
						+ " the input stream is not a valid stream in "
						+ text(input) + ", the stream needst to be of "
						+ "type std_input(). Currently, it is type " + s);
				return;
			}
		} else {
			error("line " + input.getLine() + ": "
					+ "the input stream is currently undefined in "
					+ text(input));
			return;
		}

		// Remove output stream since it is not needed after this point
		input.deleteChild(0);
    }

    public void ifstat(DashAST cond) {
        if ( cond.evalType != _boolean ) {
            error("line " + cond.getLine() + ": " +
            		"if condition "+text(cond)+
                           " must have boolean type in "+
                           text((DashAST)cond.getParent()));
        }
    }
    
    public void loopstat(DashAST cond) {
        if ( cond.evalType != _boolean ) {
            error("line " + cond.getLine() + ": " +
            		"loop condition "+text(cond)+
                           " must have boolean type in "+
                           text((DashAST)cond.getParent()));
        }
    }
    
    public void iterator(DashAST id, DashAST vector) {
        if ( vector.evalType.getTypeIndex() != tVECTOR &&
        		vector.evalType.getTypeIndex() != tINTERVAL) {
            error("line " + vector.getLine() + ": " +
            		"loop vector "+text(vector)+
                           " must have vector or interval type in "+
                           text((DashAST)vector.getParent()));
            return;
        }
        
        if (vector.evalType.getTypeIndex() == tINTERVAL) {
        	id.symbol.type = _integer;
        	id.evalType = _integer;
        }
        
        if (vector.evalType.getTypeIndex() == tVECTOR) {
        	VectorType vType = (VectorType) vector.evalType;
        	id.symbol.type = vType.elementType;
        	id.evalType = vType.elementType;
        }
    }
    
    public Type by(DashAST interval, DashAST by) {
    	if ( interval.evalType.getTypeIndex() != tINTERVAL &&
    			interval.evalType.getTypeIndex() != tVECTOR) {
            error("line " + interval.getLine() + ": " +
            		" left hand side of by statement" +
                           " must be of type interval in "+
                           text((DashAST)interval.getParent()));
            return null;
        }
    	
    	if ( by.evalType.getTypeIndex() != tINTEGER) {
            error("line " + by.getLine() + ": " +
            		" right hand side of by statement" +
                           " must be of type integer in "+
                           text((DashAST)by.getParent()));
            return null;
        }
    	if (interval.evalType.getTypeIndex() == tINTERVAL)
    		return new VectorType(_integer, 0);
    	
    	return interval.evalType;
    }
    
    public Type concat(DashAST lhs, DashAST rhs) {
    	Type type = null;
    	int type_index = -1;
    	
    	if ( lhs.evalType.getTypeIndex() == tVECTOR) {
    		VectorType vType = (VectorType) lhs.evalType;
    		int lhs_index = vType.elementType.getTypeIndex();
            if (lhs_index > type_index) {
            	type_index = lhs_index;
            	type = vType.elementType;
            }
        } else if ( lhs.evalType.getTypeIndex() == tINTERVAL) {
            if (tINTEGER > type_index) {
            	type_index = tINTEGER;
            	type = _integer;
            }
        } else {
        	int lhs_index = lhs.evalType.getTypeIndex();
            if (lhs_index > type_index) {
            	type_index = lhs_index;
            	type = lhs.evalType;
            }
        }
    	
    	if ( rhs.evalType.getTypeIndex() == tVECTOR) {
    		VectorType vType = (VectorType) rhs.evalType;
    		int rhs_index = vType.elementType.getTypeIndex();
            if (rhs_index > type_index) {
            	type_index = rhs_index;
            	type = vType.elementType;
            }
        } else if ( rhs.evalType.getTypeIndex() == tINTERVAL) {
            if (tINTEGER > type_index) {
            	type_index = tINTEGER;
            	type = _integer;
            }
        } else {
        	int rhs_index = rhs.evalType.getTypeIndex();
            if (rhs_index > type_index) {
            	type_index = rhs_index;
            	type = rhs.evalType;
            }
        }
    	
    	return new VectorType(type, 0);
    }
    
    public Type dotProduct(DashAST lhs, DashAST rhs) {
    	Type type = null;
    	int type_index = -1;
    	
    	if ( lhs.evalType.getTypeIndex() == tVECTOR) {
    		VectorType vType = (VectorType) lhs.evalType;
    		int lhs_index = vType.elementType.getTypeIndex();
            if (lhs_index > type_index) {
            	type_index = lhs_index;
            	type = vType.elementType;
            }
        } else if ( lhs.evalType.getTypeIndex() == tINTERVAL) {
            if (tINTEGER > type_index) {
            	type_index = tINTEGER;
            	type = _integer;
            }
        } else if ( lhs.evalType.getTypeIndex() == tMATRIX) {
    		MatrixType matType = (MatrixType) lhs.evalType;
    		int lhs_index = matType.elementType.getTypeIndex();
            if (lhs_index > type_index) {
            	type_index = lhs_index;
            	type = matType.elementType;
            }
        }
    	
    	if ( rhs.evalType.getTypeIndex() == tVECTOR) {
    		VectorType vType = (VectorType) rhs.evalType;
    		int rhs_index = vType.elementType.getTypeIndex();
            if (rhs_index > type_index) {
            	type_index = rhs_index;
            	type = vType.elementType;
            }
        } else if ( rhs.evalType.getTypeIndex() == tINTERVAL) {
            if (tINTEGER > type_index) {
            	type_index = tINTEGER;
            	type = _integer;
            }
        } else if (rhs.evalType.getTypeIndex() == tMATRIX) {
    		MatrixType matType = (MatrixType) rhs.evalType;
    		int rhs_index = matType.elementType.getTypeIndex();
            if (rhs_index > type_index) {
            	type_index = rhs_index;
            	type = matType.elementType;
            }
        }
    	
    	if (lhs.evalType.getTypeIndex() == tMATRIX &&
    			rhs.evalType.getTypeIndex() == tMATRIX) {
    		return new MatrixType(type, 0, 0);
    	}
    	
    	if ((lhs.evalType.getTypeIndex() == tINTERVAL ||
    			lhs.evalType.getTypeIndex() == tVECTOR) &&
    			(rhs.evalType.getTypeIndex() == tINTERVAL ||
    			rhs.evalType.getTypeIndex() == tVECTOR)) {
    		return type;
    	}
    	
    	error("line " + lhs.getLine() + ": " +
        		" ** operator must take two vectors or to matrices in "+
                       text((DashAST)lhs.getParent()));
    	
    	return null;
    }
    
    public boolean typeCast(DashAST typecast, DashAST list) {
    	
    	if (typecast.evalType.getTypeIndex() == tTUPLE) {
    		TupleTypeSymbol tuple = (TupleTypeSymbol) typecast.evalType;
    		
    		if (list.getType() == DashLexer.TUPLE_LIST) {
    			list.evalType = tuple;
    			
    			for (int i = 0; i < tuple.fields.size(); i++) {
        			VariableSymbol var = (VariableSymbol) tuple.fields.get(i);
        			Type type = var.type;
        			
        			DashAST expr = new DashAST(new CommonToken(DashLexer.EXPR, "EXPR"));
        			expr.evalType = type;
        			
        			DashAST type_cast = new DashAST(new CommonToken(DashLexer.TYPECAST, "TYPECAST"));
        			type_cast.evalType = type;
    
        			type_cast.addChild(list.getChild(i));
        			expr.addChild(type_cast);
        			
        			list.replaceChildren(i, i, expr);
        		}
    			
    			return true;
    		}	
    	}
    	
    	return false;
    }
    
    public boolean canAssignTo(Type valueType,Type destType,Type promotion) {
    	if (valueType.getTypeIndex() == tTUPLE  && destType.getTypeIndex() == tTUPLE) {
    		TupleTypeSymbol valueTuple = (TupleTypeSymbol)valueType;
    		TupleTypeSymbol destTuple = (TupleTypeSymbol)destType;
    		
    		if (valueTuple.fields.size() != destTuple.fields.size()) {
        		return false;
        	}
        	
        	for (int i = 0; i < valueTuple.fields.size(); i++) {
        		VariableSymbol f_var = (VariableSymbol) valueTuple.fields.get(i);
        		VariableSymbol a_var = (VariableSymbol) destTuple.fields.get(i);
        		
        		Type f = f_var.type;
        		Type a = a_var.type;
        		
        		Type promoteToType = promote(f, a);
        		
                if ( !canAssignTo(f, a, promoteToType) ) {
        			return false;
        		}
        	}
        	
        	return true;
    	}
    	
    	Type rhsType = promotion;
    	
    	if (rhsType == null)
    		rhsType = valueType;
    	
    	if (rhsType.getTypeIndex() == tINTERVAL  && destType.getTypeIndex() == tVECTOR) {
    		if (((VectorType)destType).elementType.getTypeIndex() == tINTEGER)
    			return true;
    		
    		return false;
    	}
    	
    	if (destType.getTypeIndex() == tINTERVAL) {
			if (rhsType.getTypeIndex() == tINTERVAL)
				return true;
			
			return false;
		} else if (destType.getTypeIndex() == tVECTOR) {
			if (rhsType.getTypeIndex() == tMATRIX)
				return false;
			
			Type element = ((VectorType)destType).elementType;
			
			if (rhsType.getTypeIndex() == tINTERVAL) {
				if (element.getTypeIndex() == tINTEGER)
					return true;
				
				return false;
			}
			
			if (rhsType.getTypeIndex() == tVECTOR) {
				Type element2 = ((VectorType)rhsType).elementType;
				return element == element2 || element == promote(element2, element);
			}
			
			return element == rhsType || element == promote(rhsType, element);
		} else if (destType.getTypeIndex() == tMATRIX) {
			if (rhsType.getTypeIndex() == tVECTOR ||
					rhsType.getTypeIndex() == tINTERVAL)
				return false;
			
			Type element = ((MatrixType)destType).elementType;

			if (rhsType.getTypeIndex() == tMATRIX) {
				Type element2 = ((MatrixType)rhsType).elementType;
				return element == element2  || element == promote(element2, element);
			}
			
			return element == rhsType || element == promote(rhsType, element);
		}
    	
    	if (promotion == null)
    		promotion = _null;
    	
        // either types are same or value was successfully promoted
        return valueType.getTypeIndex()==destType.getTypeIndex() || 
        		promotion.getTypeIndex()==destType.getTypeIndex();
    }
    
    private static CommonToken convertFromNull(Type evalType) {
        int typeIndex = evalType.getTypeIndex();
        
        if (typeIndex == tINTEGER) {
            return new CommonToken(DashLexer.INTEGER, "0");
          }
          // if real declaration add ^(EXPR 0.0)
          else if (typeIndex == tREAL) {
            return new CommonToken(DashLexer.REAL, "0.0");
          }
          // if character declaration add ^(EXPR '\0')
          else if (typeIndex == tCHARACTER) {
            return new CommonToken(DashLexer.CHARACTER, "'\\0'");
          }
          // if boolean declaration add ^(EXPR false)
          else if (typeIndex == tBOOLEAN) {
              return new CommonToken(DashLexer.False, "false");
          }
        return null;
    }
    
    private static CommonToken convertFromIdentity(Type evalType) {
        int typeIndex = evalType.getTypeIndex();
        
        if (typeIndex == tINTEGER) {
            return new CommonToken(DashLexer.INTEGER, "1");
          }
          // if real declaration add ^(EXPR 0.0)
          else if (typeIndex == tREAL) {
            return new CommonToken(DashLexer.REAL, "1.0");
          }
          // if character declaration add ^(EXPR '\0')
          else if (typeIndex == tCHARACTER) {
            return new CommonToken(DashLexer.CHARACTER, "'" + Character.toString ((char) 1) + "'");
          }
          // if boolean declaration add ^(EXPR false)
          else if (typeIndex == tBOOLEAN) {
              return new CommonToken(DashLexer.True, "true");
          }
        return null;
    }
    
    public static DashAST getExprForNull(Type type) {
        if (type == null) {
          return null;
        }
        
        if (type.getTypeIndex() == tINTERVAL) {
        	DashAST expr = new DashAST(new CommonToken(DashLexer.EXPR, "EXPR"));
            expr.evalType = type;
            
            DashAST range = new DashAST(new CommonToken(DashLexer.RANGE, ".."));
            DashAST lower = new DashAST(new CommonToken(DashLexer.INTEGER, "0"));
            DashAST upper = new DashAST(new CommonToken(DashLexer.INTEGER, "0"));
            
            range.addChild(lower);
            range.addChild(upper);
            
            expr.addChild(range);
            return expr;
        }
        
        type = getElementType(type);
        
        DashAST expr = new DashAST(new CommonToken(DashLexer.EXPR, "EXPR"));
        expr.evalType = type;
        CommonToken token = convertFromNull(type);
        
        if (token == null)
            return null;

        expr.addChild(new DashAST(token));
        return expr;
      }
    
    public static DashAST getExprForIdentity(Type type) {
        if (type == null) {
          return null;
        }
        
        if (type.getTypeIndex() == tINTERVAL) {
        	DashAST expr = new DashAST(new CommonToken(DashLexer.EXPR, "EXPR"));
            expr.evalType = type;
            
            DashAST range = new DashAST(new CommonToken(DashLexer.RANGE, ".."));
            DashAST lower = new DashAST(new CommonToken(DashLexer.INTEGER, "1"));
            DashAST upper = new DashAST(new CommonToken(DashLexer.INTEGER, "1"));
            
            range.addChild(lower);
            range.addChild(upper);
            
            expr.addChild(range);
            return expr;
        }
        
        type = getElementType(type);
        
        DashAST expr = new DashAST(new CommonToken(DashLexer.EXPR, "EXPR"));
        expr.evalType = type;
        CommonToken token = convertFromIdentity(type);
        
        if (token == null)
            return null;

        expr.addChild(new DashAST(token));
        return expr;
      }

    public String text(DashAST t) {
        String ts = "";
        if ( t.evalType!=null ) ts = ":<"+t.evalType+">";
        return tokens.toString(t.getTokenStartIndex(),
                               t.getTokenStopIndex())+ts;
    }
    
    public String toString() { return globals.toString(); }
}