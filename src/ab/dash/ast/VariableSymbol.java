package ab.dash.ast;

/***
 * Excerpted from "Language Implementation Patterns",
 * published by The Pragmatic Bookshelf.
 * Copyrights apply to this code. It may not be used to create training material, 
 * courses, books, articles, and the like. Contact us if you are in doubt.
 * We make no guarantees that this code is fit for any purpose. 
 * Visit http://www.pragmaticprogrammer.com/titles/tpdsl for more book information.
***/
/** Represents a variable definition (name,type) in symbol table */
public class VariableSymbol extends Symbol {
	public Specifier specifier;
	public String initialValue;
	public VariableSymbol(String name, Type type, Specifier specifier) 
	{ 
		super(name, type); 
		this.specifier = specifier;
		
		this.initialValue = "";
	}
}