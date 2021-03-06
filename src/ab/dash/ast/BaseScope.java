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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public abstract class BaseScope implements Scope {
	Scope enclosingScope; // null if global (outermost) scope
	Map<String, Symbol> symbols = new LinkedHashMap<String, Symbol>();

    public BaseScope(Scope parent) { this.enclosingScope = parent;	}

    public Symbol resolve(String name) {
		Symbol s = symbols.get(name);
        if ( s!=null ) return s;
		// if not here, check any enclosing scope
		if ( enclosingScope != null ) return enclosingScope.resolve(name);
		return null; // not found
	}
    
    public Symbol resolveInCurrentScope(String name) {
        Symbol s = symbols.get(name);
        if ( s!=null ) return s;

        return null; // not found
    }

	public void define(Symbol sym) {
		symbols.put(sym.name, sym);
		sym.scope = this; // track the scope in each symbol
	}

    public Scope getEnclosingScope() { return enclosingScope; }

	public String toString() { return symbols.keySet().toString(); }
	
	public Set<String> keys() {
	    //ArrayList<String> list = new ArrayList<String>(symbols.keySet());
	    //Collections.sort(list);
	    //return list;
	    return symbols.keySet();
	}
	
	public Collection<Symbol> getDefined() { return symbols.values(); }
}