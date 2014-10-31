package ab.dash.testing;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.TreeSet;

import org.antlr.runtime.ANTLRFileStream;
import org.antlr.runtime.CharStream;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.TokenRewriteStream;
import org.antlr.runtime.tree.CommonTreeNodeStream;

import ab.dash.DashLexer;
import ab.dash.DashParser;
import ab.dash.Def;
import ab.dash.DefineTupleTypes;
import ab.dash.Types;
import ab.dash.ast.DashAST;
import ab.dash.ast.SymbolTable;
import ab.dash.opt.ConstantFolding;
import ab.dash.opt.ConstantPropagation;
import ab.dash.opt.Ref;
import ab.dash.opt.RemoveUnusedVariables;


public class TestConstantFoldingAndPropagation {
	
	public static void parseFile(String name, String file) throws RecognitionException {
		CharStream input = null;
		try {
			input = new ANTLRFileStream(file);
		} catch (IOException e) {
			System.err.print("Invalid program filename: ");
			System.err.println(file);
			System.exit(1);
		}
		DashLexer lexer = new DashLexer(input);
		final TokenRewriteStream tokens = new TokenRewriteStream(lexer);
		DashParser parser = new DashParser(tokens);
		parser.setTreeAdaptor(DashAST.dashAdaptor);
		DashParser.program_return r = parser.program();
		
		if (lexer.getErrorCount() > 0) {
        	return;
        }
        
        if (parser.getErrorCount() > 0) {
        	return;
        }

		DashAST tree = (DashAST) r.getTree();

		CommonTreeNodeStream nodes = new CommonTreeNodeStream(tree);
		nodes.setTokenStream(tokens);

		SymbolTable symtab = new SymbolTable(tokens); // make global scope,
										              // types
		
		Def def = new Def(nodes, symtab, false); // use custom constructor
		def.downup(tree); // trigger symtab actions upon certain subtrees
		

		// RESOLVE SYMBOLS, COMPUTE EXPRESSION TYPES
		nodes.reset();
		Types typeComp = new Types(nodes, symtab);
		typeComp.downup(tree); // trigger resolve/type computation actions
		
		if (symtab.getErrorCount() > 0) {
			return;
		}
		
		nodes.reset();
		DefineTupleTypes tupleTypeComp = new DefineTupleTypes(nodes, symtab);
		tupleTypeComp.debug_off();
		tupleTypeComp.downup(tree); // trigger resolve/type computation actions
		
		if (symtab.getErrorCount() > 0) {
			return;
		}
		
		System.out.println(tree.toStringTree());
		
		boolean fixed_state = false;
		boolean remove_unused = false;
		
		ConstantFolding opt1 = new ConstantFolding(nodes, symtab);
		opt1.setTreeAdaptor(DashAST.dashAdaptor);

		ConstantPropagation opt2 = new ConstantPropagation(symtab);
		int iterations = 0;
		String tree_before = tree.toStringTree();
		
		while (!fixed_state) {
			iterations++;
			System.out.println("Iteration: " + iterations);
			
			nodes.reset();
			opt1.downup(tree); // trigger resolve/type computation actions
			
			nodes.reset();
	    	opt2.optimize(tree);
			
			if (remove_unused) {
				Ref opt3 = new Ref(nodes, true);
				nodes.reset();
				opt3.downup(tree);
				
				TreeSet<Integer> refs = opt3.getRefs();
				
				RemoveUnusedVariables opt4 = new RemoveUnusedVariables(refs, true);
				nodes.reset();
				tokens.reset();
				opt4.optimize(tree);
			}
			
			String tree_after = tree.toStringTree();
			fixed_state = tree_before.equals(tree_after);
			tree_before = tree_after;
		}
		
		System.out.println(tree.toStringTree());
	}


	public static void showFiles(File[] files) throws RecognitionException {
		Arrays.sort(files, new Comparator<File>() {
		    public int compare(File f1, File f2) {
		        return f1.getName().compareTo(f2.getName());
		    }
		});
		
		for (File file : files) {
			if (file.isDirectory()) {
				System.out.println("Directory: " + file.getName());
				showFiles(file.listFiles()); // Calls same method again.
			} else {
				int i = file.getName().lastIndexOf('.');
	        	if (i > 0) {
	        	    String extension = file.getName().substring(i+1);
	        	    
	        	    if (extension.equals("db")) {
		        	    System.out.println("File: " + file.getName());
			            parseFile(file.getName(), file.getPath());
	        	    }
	        	}
			}
		}
	}

	public static void main(String[] args) throws RecognitionException {
		File[] files = new File("TestConstantFoldingAndPropagation/").listFiles();
		showFiles(files);
	}
}
