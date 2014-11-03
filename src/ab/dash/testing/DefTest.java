package ab.dash.testing;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.antlr.runtime.RecognitionException;
import org.junit.Before;
import org.junit.Test;

import ab.dash.ast.SymbolTable;
import ab.dash.exceptions.LexerException;
import ab.dash.exceptions.ParserException;

public class DefTest extends BaseTest {
    
    @Test // Check that the globals are what we expect for a program that just defines main
    public void simpleMain() throws RecognitionException, LexerException, ParserException {        
        String[] args = new String[] {"TestPrograms/01SimpleMain/simpleMain.ds"};
        SymbolTable symtab = DefTestMain.main(args);
        base_globals.add("main");
        assertEquals(base_globals, symtab.globals.keys());
    }
    
    
    @Test // Check that the globals are declared correctly when local variables are added
    public void localVariables() throws RecognitionException, LexerException, ParserException {        
        String[] args = new String[] {"TestPrograms/02VariableDeclarationInMain/variableDeclarationInMain.ds"};
        SymbolTable symtab = DefTestMain.main(args);
        base_globals.add("main");
        base_globals.add("out");
        assertEquals(base_globals, symtab.globals.keys());
    }
    
    @Test // Test with if statements
    public void ifStatement() throws RecognitionException, LexerException, ParserException {        
        String[] args = new String[] {"TestPrograms/04SimpleIfStatement/simpleIfStatement.ds"};
        SymbolTable symtab = DefTestMain.main(args);
        base_globals.add("main");
        base_globals.add("out");
        assertEquals(base_globals, symtab.globals.keys());    
    }

    @Test // Test with tuples
    public void tuples() throws RecognitionException, LexerException, ParserException {        
        String[] args = new String[] {"TestPrograms/05Tuples/tuples.ds"};
        SymbolTable symtab = DefTestMain.main(args);
        base_globals.add("main");
        base_globals.add("out");
        assertEquals(base_globals, symtab.globals.keys());    
    }

    @Test // Test with type inference
    public void typeInference() throws RecognitionException, LexerException, ParserException {        
        String[] args = new String[] {"TestPrograms/06TypeInference/typeInference.ds"};
        SymbolTable symtab = DefTestMain.main(args);
        base_globals.add("main");
        base_globals.add("out");
        base_globals.add("b");
        assertEquals(base_globals, symtab.globals.keys());    
    }
    
    @Test // Test with integers
    public void integers() throws RecognitionException, LexerException, ParserException {        
        String[] args = new String[] {"TestPrograms/07Integers/integers.ds"};
        SymbolTable symtab = DefTestMain.main(args);
        base_globals.add("main");
        base_globals.add("out");
        assertEquals(base_globals, symtab.globals.keys());    
    }

    @Test // Test with procedures
    public void procedures() throws RecognitionException, LexerException, ParserException {        
        String[] args = new String[] {"TestPrograms/08Procedures/procedureWithNoReturnType.ds"};
        SymbolTable symtab = DefTestMain.main(args);
        base_globals.add("main");
        base_globals.add("out");
        base_globals.add("no_return");
        assertEquals(base_globals, symtab.globals.keys());    
    }

    @Test // Test with typedefs
    public void typedef() throws RecognitionException, LexerException, ParserException {        
        String[] args = new String[] {"TestPrograms/09Typedef/typedef.ds"};
        SymbolTable symtab = DefTestMain.main(args);
        base_globals.add("main");
        base_globals.add("out");
        base_globals.add("x");
        assertEquals(base_globals, symtab.globals.keys());    
    }
    
    @Test // Test with multiple functions
    public void multipleFunctions() throws RecognitionException, LexerException, ParserException {        
        String[] args = new String[] {"TestPrograms/10Functions/multipleFunctions.ds"};
        SymbolTable symtab = DefTestMain.main(args);
        base_globals.add("main");
        base_globals.add("add");
        base_globals.add("squared");
        assertEquals(base_globals, symtab.globals.keys());    
    }

     // Test multiple functions
     // Test empty program

}