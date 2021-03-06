package ab.dash;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.antlr.stringtemplate.StringTemplate;
import org.antlr.stringtemplate.StringTemplateGroup;

import ab.dash.DashLexer;
import ab.dash.ast.*;


public class LLVMIRGenerator {
	private StringTemplateGroup stg;
	private StringTemplate template;
	private SymbolTable symtab;
	private Stack<Integer> loop_stack;
	private Map<Integer, String> typeIndexToName;
	private Set<String> vector_sizes;
	
	private boolean debug_mode = false;
	
	public enum LLVMOps {
	    AND, OR, XOR, ADD, SUB, MULT, DIV, MOD, POWER, DOTPRODUCT, EQ, NE, LT, LE, GT, GE, CONCAT
	}
	
	public LLVMIRGenerator(StringTemplateGroup stg, SymbolTable symtab) {		
		this.stg = stg;
		this.symtab = symtab;
		this.loop_stack = new Stack<Integer>();
		this.vector_sizes = new HashSet<String>();
		
		/*
		 * These are the prefixes to the vector operation string template
		 * methods and to the <type>_type() string template methods.
		 */
		typeIndexToName = new HashMap<Integer, String>();
		typeIndexToName.put(SymbolTable.tBOOLEAN, "bool");
		typeIndexToName.put(SymbolTable.tCHARACTER, "char");
		typeIndexToName.put(SymbolTable.tINTEGER, "int");
		typeIndexToName.put(SymbolTable.tREAL, "real");
	}
	
	public String toString() {
		return this.template.toString();
	}
	
	public void debug_on() {
    	debug_mode = true;
    }
    
    public void debug_off() {
    	debug_mode = false;
    }
    
    private void debug(Object msg) {
    	if (debug_mode)
    		System.out.println(msg);
    }
	
	public void build(DashAST tree) {
		this.template = exec(tree);
	}
	
	protected StringTemplate exec(DashAST t) {
		debug("Parsing: " + t + ", ");
		
		switch(t.getToken().getType()) {
		case DashLexer.PROGRAM:
		{	
			// Generate Globals
			debug("\n\nCreated Globals:");

			String global_vars = "";
			String global_code = "";
			for (Symbol s : symtab.globals.getDefined()) {
				if (!(s instanceof MethodSymbol)) {
					if (s.type != null) {

						if (s.type.getTypeIndex() == SymbolTable.tINTERVAL) {
							StringTemplate alloc = stg
									.getInstanceOf("interval_alloc_global");
							alloc.setAttribute("sym_id", s.id);
							global_code += alloc.toString() + "\n";
						}

						debug(s);

						int type = s.type.getTypeIndex();
						StringTemplate template = null;
						if (type == SymbolTable.tINTEGER) {
							template = stg.getInstanceOf("int_init_global");
						} else if (type == SymbolTable.tREAL) {
							template = stg.getInstanceOf("real_init_global");
						} else if (type == SymbolTable.tCHARACTER) {
							template = stg.getInstanceOf("char_init_global");
						} else if (type == SymbolTable.tBOOLEAN) {
							template = stg.getInstanceOf("bool_init_global");
						} else if (type == SymbolTable.tTUPLE) {
							template = stg.getInstanceOf("tuple_init_global");
							TupleTypeSymbol tuple = (TupleTypeSymbol) s.type;

							String init = "";

							for (int i = 0; i < tuple.fields.size(); i++) {
								VariableSymbol member = (VariableSymbol) tuple.fields
										.get(i);
								int member_type = member.type.getTypeIndex();

								if (member_type == SymbolTable.tBOOLEAN)
									init += "i1 0";
								else if (member_type == SymbolTable.tCHARACTER)
									init += "i8 0";
								else if (member_type == SymbolTable.tINTEGER)
									init += "i32 0";
								else if (member_type == SymbolTable.tREAL)
									init += "float 0.0";

								if (i < tuple.fields.size() - 1)
									init += ", ";
							}

							template.setAttribute("init", init);
							template.setAttribute("type_id",
									tuple.tupleTypeIndex);
						} else if (type == SymbolTable.tINTERVAL) {
							template = stg
									.getInstanceOf("interval_init_global");
						} else if (type == SymbolTable.tVECTOR) {
							template = stg.getInstanceOf("vector_init_global");
						} else if (type == SymbolTable.tMATRIX) {
							template = stg.getInstanceOf("matrix_init_global");
						}

						if (template != null) {
							template.setAttribute("sym_id", s.id);
							global_vars += template.toString() + "\n";
						}
					}
				}
			}

			debug("\n\nGlobals in Symbol Table:");
			debug(symtab.globals);
			
			// Generate Code
			String code = "";
			for(int i = 0; i < t.getChildCount(); i++) {
				if (!(t.getChild(i).hasAncestor(DashLexer.PROCEDURE_DECL) ||
						t.getChild(i).hasAncestor(DashLexer.FUNCTION_DECL)) && 
						t.getChild(i).getType() == DashLexer.VAR_DECL) {
					global_code += exec((DashAST)t.getChild(i)).toString() + "\n";
				} else {
					code += exec((DashAST)t.getChild(i)).toString() + "\n";
					
				}
			}
			
			
			
			// Generate Types
			debug("\n\nCreated Types:");

			String type_vars = "";
			for (int id = 0; id < symtab.tuples.size(); id++) {
				ArrayList<Type> fields = symtab.tuples.get(id);
				String types = "";
				for (int i = 0; i < fields.size(); i++) {
					int type = fields.get(i).getTypeIndex();
					
					StringTemplate template = null;
					if (type == SymbolTable.tINTEGER) {
						template = stg.getInstanceOf("int_type");
					} else if (type == SymbolTable.tREAL) {
						template = stg.getInstanceOf("real_type");
					} else if (type == SymbolTable.tCHARACTER) {
						template = stg.getInstanceOf("char_type");
					} else if (type == SymbolTable.tBOOLEAN) {
						template = stg.getInstanceOf("bool_type");
					}
					
					types += template.toString();
					if (i < fields.size() - 1) {
						types += ",\n";
					}
				}
				
				StringTemplate template = stg.getInstanceOf("tuple");
				template.setAttribute("types", types);
				template.setAttribute("id", id);
				
				type_vars += template.toString() + "\n\n";
			}
			
			ArrayList<StringTemplate> libs = new ArrayList<StringTemplate>();
			StringTemplate lib = null;
			
			lib = stg.getInstanceOf("runtime_library_vector");
			lib.setAttribute("type", "bool");
			lib.setAttribute("llvm_type", stg.getInstanceOf("bool_type"));
			libs.add(lib);
			
			lib = stg.getInstanceOf("runtime_library_vector");
			lib.setAttribute("type", "char");
			lib.setAttribute("llvm_type", stg.getInstanceOf("char_type"));
			libs.add(lib);
			
			lib = stg.getInstanceOf("runtime_library_vector");
			lib.setAttribute("type", "int");
			lib.setAttribute("llvm_type", stg.getInstanceOf("int_type"));
			libs.add(lib);
			
			lib = stg.getInstanceOf("runtime_library_vector");
			lib.setAttribute("type", "real");
			lib.setAttribute("llvm_type", stg.getInstanceOf("real_type"));
			libs.add(lib);
			
			lib = stg.getInstanceOf("runtime_library_vector_arithmetic");
			lib.setAttribute("type", "int");
			lib.setAttribute("llvm_type", stg.getInstanceOf("int_type"));
			libs.add(lib);
			
			lib = stg.getInstanceOf("runtime_library_vector_arithmetic");
			lib.setAttribute("type", "real");
			lib.setAttribute("llvm_type", stg.getInstanceOf("real_type"));
			libs.add(lib);
			
			StringTemplate template = stg.getInstanceOf("program");
			template.setAttribute("libs", libs);
			template.setAttribute("type_defs", type_vars);
			template.setAttribute("globals", global_vars);
			template.setAttribute("global_code", global_code);
			template.setAttribute("code", code);
			
			return template;
		}
		
		case DashLexer.FUNCTION_DECL:
		case DashLexer.PROCEDURE_DECL:
		{
			Symbol sym = ((DashAST)t.getChild(0)).symbol;
			int sym_id = sym.id;
			
			Type type = sym.type;
			
			if (sym.name.equals("main")) {
				StringTemplate code = exec((DashAST)t.getChild(1));
				StringTemplate template = stg.getInstanceOf("function_main");
				template.setAttribute("code", code);
				template.setAttribute("id", t.llvmResultID);
				return template;
			}

			List<StringTemplate> args = new ArrayList<StringTemplate>();
			for (int i = 1; i < t.getChildCount() - 1; i++) {
				DashAST argument_node = ((DashAST) t.getChild(i).getChild(1));
				VariableSymbol arg_var = (VariableSymbol)argument_node.symbol;
				Type arg_type = arg_var.type;
				StringTemplate type_template = getType(arg_type);

				String argTemplate = "declare_argument";
				StringTemplate arg = stg.getInstanceOf(argTemplate);
				arg.setAttribute("arg_id", arg_var.id);
				arg.setAttribute("arg_type", type_template);
				arg.setAttribute("id", argument_node.llvmResultID);

				args.add(arg);
			}

			StringTemplate code = exec((DashAST)t.getChild(t.getChildCount() - 1));
			String code_s = code.toString() + "\n";
			
			if (type.getTypeIndex() == SymbolTable.tVOID) {
				code_s += "\nret void\n";
			}
			
			StringTemplate template = null;
			if (type.getTypeIndex() == SymbolTable.tTUPLE) {
				template = stg.getInstanceOf("function_returning_tuple");
				template.setAttribute("type_id", ((TupleTypeSymbol)type).tupleTypeIndex);
			}
			else {
				template = stg.getInstanceOf("function");
				template.setAttribute("return_type", getType(type));
			}
			
			template.setAttribute("code", code_s);
			template.setAttribute("args", args);
			template.setAttribute("sym_id", sym_id);
			template.setAttribute("id", t.llvmResultID);
			
			return template;
		}
		
		case DashLexer.CALL:
		{
			DashAST method_node = (DashAST) t.getChild(0);
			MethodSymbol method = (MethodSymbol)method_node.symbol;
			
			Type method_type = method.type;
			
			// Arguments
			List<StringTemplate> code = new ArrayList<StringTemplate>();
			List<StringTemplate> args = new ArrayList<StringTemplate>();
			DashAST argument_list = (DashAST) t.getChild(1);
			StringTemplate stackSave = null;
			
			if (method.getShortName().equals("stream_state")) {
				StringTemplate template = null;
				template = stg.getInstanceOf("call_stream_state");
				template.setAttribute("id", t.llvmResultID);
				return template;
			}
			
			if (method.getShortName().equals("length")) {
				StringTemplate template = null;
				template = stg.getInstanceOf("call_length");
				
				StringTemplate getVector = exec((DashAST)argument_list.getChild(0));
				
				Type type = ((DashAST)argument_list.getChild(0)).evalType;
				
				if (type.getTypeIndex() == SymbolTable.tINTERVAL) {
					StringTemplate interval = stg.getInstanceOf("interval_to_vector");
					interval.setAttribute("id", DashAST.getUniqueId());
					interval.setAttribute("interval_var_expr", getVector);
					interval.setAttribute("interval_var_expr_id", getVector.getAttribute("id"));
					
					getVector = interval;
				}

				template.setAttribute("id", t.llvmResultID);
				template.setAttribute("vector_expr", getVector);
				template.setAttribute("vector_expr_id", getVector.getAttribute("id"));
				return template;
			}
			
			if (method.getShortName().equals("rows")) {
				StringTemplate template = null;
				template = stg.getInstanceOf("call_rows");
				
				StringTemplate getMatrix = exec((DashAST)argument_list.getChild(0));

				template.setAttribute("id", t.llvmResultID);
				template.setAttribute("matrix_expr", getMatrix);
				template.setAttribute("matrix_expr_id", getMatrix.getAttribute("id"));
				return template;
			}
			
			if (method.getShortName().equals("columns")) {
				StringTemplate template = null;
				template = stg.getInstanceOf("call_columns");
				
				StringTemplate getMatrix = exec((DashAST)argument_list.getChild(0));

				template.setAttribute("id", t.llvmResultID);
				template.setAttribute("matrix_expr", getMatrix);
				template.setAttribute("matrix_expr_id", getMatrix.getAttribute("id"));
				return template;
			}
			
			if (method.getShortName().equals("reverse")) {
				StringTemplate template = null;
				template = stg.getInstanceOf("call_reverse");
				
				StringTemplate getVector = exec((DashAST)argument_list.getChild(0));
				Type type = ((DashAST)argument_list.getChild(0)).evalType;
				int elementTypeIndex = -1;
				if (type.getTypeIndex() == SymbolTable.tINTERVAL) {
					StringTemplate interval = stg.getInstanceOf("interval_to_vector");
					interval.setAttribute("id", DashAST.getUniqueId());
					interval.setAttribute("interval_var_expr", getVector);
					interval.setAttribute("interval_var_expr_id", getVector.getAttribute("id"));
					
					getVector = interval;
					elementTypeIndex = SymbolTable.tINTEGER;
				} else {
					VectorType vType = (VectorType)type;
					elementTypeIndex = vType.elementType.getTypeIndex();
				}
				
				template.setAttribute("id", t.llvmResultID);
				template.setAttribute("type_name", typeIndexToName.get(elementTypeIndex));
				template.setAttribute("vector_expr", getVector);
				template.setAttribute("vector_expr_id", getVector.getAttribute("id"));
				return template;
			}

			for (int i = 0; i < argument_list.getChildCount(); i++) {
				DashAST arg = (DashAST)argument_list.getChild(i);
				DashAST arg_child = (DashAST)arg.getChild(0);
				Type arg_type = arg.evalType;
				
				if (((DashAST)arg.getChild(0)).promoteToType != null) {
					arg_type = ((DashAST)arg.getChild(0)).promoteToType;
				}
				
				StringTemplate llvm_arg_type = getType(arg_type);

				StringTemplate arg_template = null;
				if (arg_child.symbol instanceof VariableSymbol && 
						(arg_child.promoteToType == null ||
						arg_child.promoteToType.getTypeIndex() != SymbolTable.tREAL)) {
					VariableSymbol var_sym = (VariableSymbol)arg_child.symbol;
					arg_template = stg.getInstanceOf("pass_variable_by_reference");
					arg_template.setAttribute("var_id", var_sym.id);
					arg_template.setAttribute("arg_type", llvm_arg_type);
				} else if (arg_type.getTypeIndex() == SymbolTable.tTUPLE) {
					code.add(exec(arg));

					arg_template = stg.getInstanceOf("pass_tuple_expr_by_reference");
					arg_template.setAttribute("tuple_expr_id", arg_child.llvmResultID);
					arg_template.setAttribute("type_id", ((TupleTypeSymbol)arg_type).tupleTypeIndex);
				} else {
					if (stackSave == null) {
						stackSave = stg.getInstanceOf("save_stack");
						stackSave.setAttribute("call_id", method_node.llvmResultID);
						code.add(stackSave);
					}

					code.add(exec(arg));

					StringTemplate toStack = stg.getInstanceOf("expr_result_to_stack");
					toStack.setAttribute("expr_id", arg_child.llvmResultID);
					toStack.setAttribute("expr_type", llvm_arg_type);
					code.add(toStack);

					arg_template = stg.getInstanceOf("pass_expr_by_reference");
					arg_template.setAttribute("arg_expr_id", arg_child.llvmResultID);
					arg_template.setAttribute("arg_type", llvm_arg_type);
				}

				arg_template.setAttribute("id", arg_child.llvmResultID);

				args.add(arg_template);
			}

			StringTemplate template = null;
			if (method_type.getTypeIndex() == SymbolTable.tVOID) {
				template = stg.getInstanceOf("call_void");
			}
			else if (method_type.getTypeIndex() == SymbolTable.tTUPLE) {
				template = stg.getInstanceOf("call_returning_tuple");
				template.setAttribute("type_id", ((TupleTypeSymbol)method_type).tupleTypeIndex);
			} else if (method_type.getTypeIndex() == SymbolTable.tVECTOR) {
				VectorType vt = (VectorType) method_type;
				template = stg.getInstanceOf("call_returning_vector");
				template.setAttribute("type_name", typeIndexToName.get(vt.elementType.getTypeIndex()));
			} else if (method_type.getTypeIndex() == SymbolTable.tMATRIX) {
				MatrixType mt = (MatrixType) method_type;
				template = stg.getInstanceOf("call_returning_matrix");
				template.setAttribute("type_name", typeIndexToName.get(mt.elementType.getTypeIndex()));
			} else {
				template = stg.getInstanceOf("call");
				template.setAttribute("return_type", getType(method_type));
			}

			if (stackSave != null) {
				StringTemplate stackRestore = stg.getInstanceOf("restore_stack");
				stackRestore.setAttribute("call_id", method_node.llvmResultID);
				template.setAttribute("postcode", stackRestore);
			}

			template.setAttribute("code", code);
			template.setAttribute("args", args);
			template.setAttribute("function_id", method.id);
			template.setAttribute("id", t.llvmResultID);
			return template;
		}
		
		case DashLexer.Return:
		{
			int id = t.llvmResultID;
			
			DashAST expr = (DashAST)t.getChild(0);
			int expr_id = ((DashAST)t.getChild(0).getChild(0)).llvmResultID;

			StringTemplate expr_template = exec(expr);
			StringTemplate template = null;

			if (expr.evalType.getTypeIndex() == SymbolTable.tTUPLE) {
				TupleTypeSymbol tuple_type = (TupleTypeSymbol)expr.evalType;
				template = stg.getInstanceOf("return_tuple");
				template.setAttribute("id", id);
				template.setAttribute("type_id", tuple_type.tupleTypeIndex);

				StringTemplate assignmentTemplate = stg.getInstanceOf("tuple_assign");

				List<StringTemplate> element_assigns = new ArrayList<StringTemplate>();

				List<Symbol> fields = tuple_type.fields;
				for (int i = 0; i <	fields.size(); i++) {
					StringTemplate memberAssign = null;
					StringTemplate getMember = null;

					int field_type = fields.get(i).type.getTypeIndex();
					if (field_type == SymbolTable.tINTEGER) {
						getMember = stg.getInstanceOf("int_get_tuple_member");
						memberAssign = stg.getInstanceOf("int_tuple_assign");
					}
					else if (field_type == SymbolTable.tREAL) {
						getMember = stg.getInstanceOf("real_get_tuple_member");
						memberAssign = stg.getInstanceOf("real_tuple_assign");
					}
					else if (field_type == SymbolTable.tCHARACTER) {
						getMember = stg.getInstanceOf("char_get_tuple_member");
						memberAssign = stg.getInstanceOf("char_tuple_assign");
					}
					else if (field_type == SymbolTable.tBOOLEAN) {
						getMember = stg.getInstanceOf("bool_get_tuple_member");
						memberAssign = stg.getInstanceOf("bool_tuple_assign");
					}
					
					getMember.setAttribute("id", DashAST.getUniqueId());
					getMember.setAttribute("tuple_expr_id", expr_id);
					getMember.setAttribute("tuple_type", tuple_type.tupleTypeIndex);
					getMember.setAttribute("index", i);

					memberAssign.setAttribute("id", DashAST.getUniqueId());
					memberAssign.setAttribute("tuple_expr_id", template.getAttribute("id"));
					memberAssign.setAttribute("tuple_type", tuple_type.tupleTypeIndex);
					memberAssign.setAttribute("index", i);
					memberAssign.setAttribute("expr", getMember);
					memberAssign.setAttribute("expr_id", getMember.getAttribute("id"));

					element_assigns.add(memberAssign);
				}

				assignmentTemplate.setAttribute("rhs_expr", expr_template);
				assignmentTemplate.setAttribute("element_assigns", element_assigns);

				template.setAttribute("assign_code", assignmentTemplate);
			}
			else {
				boolean main = false;
				Symbol sym = t.symbol;
				
				if (sym != null) {
					if (sym.name.equals("main")) {
						main = true;
					}
				}
				
				if (main)
					template = stg.getInstanceOf("return_main");
				else
					template = stg.getInstanceOf("return");
				StringTemplate type_template = getType(((DashAST) t.getChild(0)).evalType);

				template.setAttribute("expr_id", expr_id);
				template.setAttribute("expr", expr_template);
				template.setAttribute("type", type_template);
				template.setAttribute("id", id);
			}
			
			return template;
		}
		
		case DashLexer.TUPLE_LIST:
		{
			TupleTypeSymbol tuple = (TupleTypeSymbol)t.evalType;

			StringTemplate template = stg.getInstanceOf("tuple_init_literal");
			template.setAttribute("id", t.llvmResultID);
			template.setAttribute("type_id", tuple.tupleTypeIndex);

			List<StringTemplate> element_exprs = new ArrayList<StringTemplate>();

			for (int i = 0; i < t.getChildCount(); i++) {
				DashAST element_node = (DashAST)t.getChild(i);
				int element_expr_id = ((DashAST)element_node.getChild(0)).llvmResultID;

				StringTemplate memberAssign = null;

				int type = element_node.evalType.getTypeIndex();
				if (type == SymbolTable.tINTEGER)
					memberAssign = stg.getInstanceOf("int_tuple_assign");
				else if (type == SymbolTable.tREAL)
					memberAssign = stg.getInstanceOf("real_tuple_assign");
				else if (type == SymbolTable.tCHARACTER)
					memberAssign = stg.getInstanceOf("char_tuple_assign");
				else if (type == SymbolTable.tBOOLEAN)
					memberAssign = stg.getInstanceOf("bool_tuple_assign");

				memberAssign.setAttribute("id", DashAST.getUniqueId());
				memberAssign.setAttribute("tuple_expr_id", t.llvmResultID);
				memberAssign.setAttribute("tuple_type", tuple.tupleTypeIndex);
				memberAssign.setAttribute("index", i);
				memberAssign.setAttribute("expr", exec(element_node));
				memberAssign.setAttribute("expr_id", element_expr_id);

				element_exprs.add(memberAssign);
			}
			
			template.setAttribute("element_exprs", element_exprs);

			return template;
		}

		case DashLexer.VECTOR_LIST:
		{
			if (t.evalType.getTypeIndex() == SymbolTable.tVECTOR) {
				VectorType vtype = (VectorType)t.evalType;
				int elementTypeIndex = vtype.elementType.getTypeIndex();
				int size = t.getChildCount();
	
				StringTemplate template = stg.getInstanceOf("vector_init_literal");
				template.setAttribute("id", t.llvmResultID);
				template.setAttribute("type_name", typeIndexToName.get(elementTypeIndex));
	
				StringTemplate sizeExpr = stg.getInstanceOf("int_literal");
				sizeExpr.setAttribute("id", DashAST.getUniqueId());
				sizeExpr.setAttribute("val", size);
	
				template.setAttribute("size_expr", sizeExpr);
				template.setAttribute("size_expr_id", sizeExpr.getAttribute("id"));
	
				List<StringTemplate> element_exprs = new ArrayList<StringTemplate>();
	
				for (int i = 0; i < t.getChildCount(); i++) {
					DashAST element_node = (DashAST)t.getChild(i);
					int element_expr_id = ((DashAST)element_node.getChild(0)).llvmResultID;
	
					StringTemplate memberAssign = null;
	
					memberAssign = stg.getInstanceOf("vector_elem_assign_known_index");
					memberAssign.setAttribute("id", DashAST.getUniqueId());
					
					StringTemplate llvmType = stg.getInstanceOf(typeIndexToName.get(elementTypeIndex) + "_type");
					memberAssign.setAttribute("llvm_type", llvmType);
					memberAssign.setAttribute("type_name", typeIndexToName.get(elementTypeIndex));
	
					memberAssign.setAttribute("vector_expr_id", t.llvmResultID);
	
					/* Increment index because Dash uses indexing begins at 1, not 0. */
					memberAssign.setAttribute("index", i + 1);
					memberAssign.setAttribute("expr", exec(element_node));
					memberAssign.setAttribute("expr_id", element_expr_id);
	
					element_exprs.add(memberAssign);
				}
				
				template.setAttribute("element_exprs", element_exprs);
	
				return template;
			} else {
				MatrixType mtype = (MatrixType)t.evalType;
				int elementTypeIndex = mtype.elementType.getTypeIndex();
				int size = t.getChildCount();
	
				StringTemplate template = stg.getInstanceOf("matrix_init_literal");
				template.setAttribute("id", t.llvmResultID);
				template.setAttribute("type_name", typeIndexToName.get(elementTypeIndex));
	
				StringTemplate sizeExpr = stg.getInstanceOf("int_literal");
				sizeExpr.setAttribute("id", DashAST.getUniqueId());
				sizeExpr.setAttribute("val", size);
	
				template.setAttribute("size_expr", sizeExpr);
				template.setAttribute("size_expr_id", sizeExpr.getAttribute("id"));
	
				List<StringTemplate> element_exprs = new ArrayList<StringTemplate>();
	
				for (int i = 0; i < t.getChildCount(); i++) {
					DashAST element_node = (DashAST)t.getChild(i);
					int element_expr_id = ((DashAST)element_node.getChild(0)).llvmResultID;
	
					StringTemplate memberAssign = null;
	
					memberAssign = stg.getInstanceOf("matrix_add_vector_literal");
					memberAssign.setAttribute("id", DashAST.getUniqueId());
					memberAssign.setAttribute("type_name", typeIndexToName.get(elementTypeIndex));
					memberAssign.setAttribute("expr", exec(element_node));
					memberAssign.setAttribute("expr_id", element_expr_id);
	
					element_exprs.add(memberAssign);
				}
				
				template.setAttribute("element_exprs", element_exprs);
	
				return template;
			}
		}
			
		case DashLexer.EXPR:
			return exec((DashAST)t.getChild(0));
			
		case DashLexer.BLOCK:
		{
			String temp = "";
			
			debug("locals: "+ t.scope);	
			
			// Alloca Local Variables
			for (Symbol s : t.scope.getDefined()) {
				int sym_id = s.id;
				int type = s.type.getTypeIndex();
				boolean valid_symbol = false;
				StringTemplate template = null;
				if (type == SymbolTable.tINTEGER) {
					template = stg.getInstanceOf("int_init_local");
					valid_symbol = true;
				} else if (type == SymbolTable.tREAL) {
					template = stg.getInstanceOf("real_init_local");
					valid_symbol = true;
				} else if (type == SymbolTable.tCHARACTER) {
					template = stg.getInstanceOf("char_init_local");
					valid_symbol = true;
				} else if (type == SymbolTable.tBOOLEAN) {
					template = stg.getInstanceOf("bool_init_local");
					valid_symbol = true;
				} else if (type == SymbolTable.tTUPLE) {
					template = stg.getInstanceOf("tuple_init_local");
					template.setAttribute("type_id", ((TupleTypeSymbol)s.type).tupleTypeIndex);
					valid_symbol = true;
				}  else if (type == SymbolTable.tINTERVAL){
					template = stg.getInstanceOf("interval_init_local");
					valid_symbol = true;
				} else if (type == SymbolTable.tVECTOR){
					template = stg.getInstanceOf("vector_init_local");
					valid_symbol = true;
				} else if (type == SymbolTable.tMATRIX) {
					template = stg.getInstanceOf("matrix_init_local");
					valid_symbol = true;
				}
				
				if (valid_symbol) {
					template.setAttribute("sym_id", sym_id);
					temp += template.toString() + "\n";
				}
			}
			
			for(int i = 0; i < t.getChildCount(); i++)
				temp += exec((DashAST)t.getChild(i)).toString() + "\n";
			
			return new StringTemplate(temp);
		}
			
		case DashLexer.If:
		{
			int id = ((DashAST)t).llvmResultID;
			
			StringTemplate expr = exec((DashAST)t.getChild(0));
			int expr_id = ((DashAST)t.getChild(0).getChild(0)).llvmResultID;
			
			StringTemplate block = exec((DashAST)t.getChild(1));
			
			StringTemplate template = null;
			if (t.getChildCount() > 2) {
				StringTemplate block2 = exec((DashAST)t.getChild(2));
				
				template = stg.getInstanceOf("if_else");
				template.setAttribute("block2", block2);
			} else {
				template = stg.getInstanceOf("if");
			}
						
			template.setAttribute("block", block);
			template.setAttribute("expr_id", expr_id);
			template.setAttribute("expr", expr);
			template.setAttribute("id", id);
			
			return template;
		}

		case DashLexer.WHILE:
		{
			int id = ((DashAST)t).llvmResultID;
			
			StringTemplate expr = exec((DashAST)t.getChild(0));
			int expr_id = ((DashAST)t.getChild(0).getChild(0)).llvmResultID;
			
			loop_stack.push(new Integer(id));
			
			StringTemplate block = exec((DashAST)t.getChild(1));
			
			loop_stack.pop();
			
			StringTemplate template = stg.getInstanceOf("while");
			
			template.setAttribute("block", block);
			template.setAttribute("expr_id", expr_id);
			template.setAttribute("expr", expr);
			template.setAttribute("id", id);
			return template;
		}
		
		case DashLexer.DOWHILE:
		{
			int id = ((DashAST)t).llvmResultID;
			
			StringTemplate expr = exec((DashAST)t.getChild(0));
			int expr_id = ((DashAST)t.getChild(0).getChild(0)).llvmResultID;
			
			loop_stack.push(new Integer(id));
			
			StringTemplate block = exec((DashAST)t.getChild(1));
			
			loop_stack.pop();
			
			StringTemplate template = stg.getInstanceOf("dowhile");
			
			template.setAttribute("block", block);
			template.setAttribute("expr_id", expr_id);
			template.setAttribute("expr", expr);
			template.setAttribute("id", id);
			return template;
		}
		
		case DashLexer.Loop:
		{
			int id = ((DashAST)t).llvmResultID;
			
			
			loop_stack.push(new Integer(id));
			
			StringTemplate block = exec((DashAST)t.getChild(0));
			
			loop_stack.pop();
			
			StringTemplate template = stg.getInstanceOf("loop");
			
			template.setAttribute("block", block);
			template.setAttribute("id", id);
			return template;
		}
		
		case DashLexer.ITERATOR:
		{	
			int index = t.getChildCount() - 1;
			
			loop_stack.push(new Integer(((DashAST)t).llvmResultID));
			
			StringTemplate block = exec((DashAST)t.getChild(index));
			
			loop_stack.pop();
			
			StringTemplate template = null;
			for (int i = index - 1; i >= 0; i--) {
				template = stg.getInstanceOf("iterator");
				
				DashAST node = (DashAST)t.getChild(i);
				
				int id = ((DashAST)node).llvmResultID;
				
				DashAST sym_node = (DashAST)node.getChild(0);
				Symbol sym = sym_node.symbol;
				int sym_id = sym.id;
				
				DashAST expr_node = (DashAST)node.getChild(1).getChild(0);
				StringTemplate expr = exec(expr_node);
				String expr_id = Integer.toString(((DashAST)expr_node.getChild(0)).llvmResultID);
				
				int elementTypeIndex = 0;
				if (expr_node.evalType.getTypeIndex() == SymbolTable.tINTERVAL) {
					StringTemplate interval = stg.getInstanceOf("interval_to_vector");
					interval.setAttribute("id", DashAST.getUniqueId());
					interval.setAttribute("interval_var_expr", expr);
					interval.setAttribute("interval_var_expr_id", expr_id);
					
					expr = interval;
					expr_id = interval.getAttribute("id").toString();
					
					elementTypeIndex = SymbolTable.tINTEGER;
					
				} else {
					elementTypeIndex = ((VectorType) expr_node.evalType).elementType.getTypeIndex();
				}
				
				StringTemplate llvmType = stg.getInstanceOf(typeIndexToName.get(elementTypeIndex) + "_type");
				
				template.setAttribute("block", block);
				template.setAttribute("expr_id", expr_id);
				template.setAttribute("expr", expr);
				template.setAttribute("type_name", typeIndexToName.get(elementTypeIndex));
				template.setAttribute("llvm_type", llvmType);
				template.setAttribute("sym_id", sym_id);
				template.setAttribute("id", id);
				
				block = template;
			}
			
			return template;
		}
		
		case DashLexer.GENERATOR:
		{	
			int index = t.getChildCount() - 1;
			
			loop_stack.push(new Integer(((DashAST)t).llvmResultID));
			
			StringTemplate block = exec((DashAST)t.getChild(index));
			int block_id = ((DashAST)t.getChild(index).getChild(0)).llvmResultID;
			
			int elementTypeIndex = ((DashAST)t.getChild(index)).evalType.getTypeIndex();
			
			loop_stack.pop();
			
			StringTemplate template = null;
			if (index == 1) {
				template = stg.getInstanceOf("generator_vector");
				
				DashAST domain_x = (DashAST)t.getChild(0);
				int id = ((DashAST)t).llvmResultID;
				
				DashAST sym_node_x = (DashAST)domain_x.getChild(0);
				Symbol sym_x = sym_node_x.symbol;
				int sym_id_x = sym_x.id;
				
				DashAST expr_node_x = (DashAST)domain_x.getChild(1).getChild(0);
				StringTemplate expr_x = exec(expr_node_x);
				String expr_id_x = Integer.toString(((DashAST)expr_node_x.getChild(0)).llvmResultID);
				
				int elementTypeIndex_x = 0;
				if (expr_node_x.evalType.getTypeIndex() == SymbolTable.tINTERVAL) {
					StringTemplate interval = stg.getInstanceOf("interval_to_vector");
					interval.setAttribute("id", DashAST.getUniqueId());
					interval.setAttribute("interval_var_expr", expr_x);
					interval.setAttribute("interval_var_expr_id", expr_id_x);
					
					expr_x = interval;
					expr_id_x = interval.getAttribute("id").toString();
					
					elementTypeIndex_x = SymbolTable.tINTEGER;
					
				} else {
					elementTypeIndex_x = ((VectorType) expr_node_x.evalType).elementType.getTypeIndex();
				}
				
				StringTemplate llvmType_x = stg.getInstanceOf(typeIndexToName.get(elementTypeIndex_x) + "_type");
				StringTemplate llvmType = stg.getInstanceOf(typeIndexToName.get(elementTypeIndex) + "_type");
				template.setAttribute("block_id", block_id);
				template.setAttribute("block", block);
				template.setAttribute("expr_id", expr_id_x);
				template.setAttribute("expr", expr_x);
				template.setAttribute("type_name", typeIndexToName.get(elementTypeIndex));
				template.setAttribute("llvm_type", llvmType);
				template.setAttribute("type_name_x", typeIndexToName.get(elementTypeIndex_x));
				template.setAttribute("llvm_type_x", llvmType_x);
				template.setAttribute("sym_id", sym_id_x);
				template.setAttribute("id", id);
			} else if (index == 2) {
				template = stg.getInstanceOf("generator_matrix");
				
				DashAST domain_x = (DashAST)t.getChild(0);
				int id = ((DashAST)t).llvmResultID;
				
				// Get row
				DashAST sym_node_x = (DashAST)domain_x.getChild(0);
				Symbol sym_x = sym_node_x.symbol;
				int sym_id_x = sym_x.id;
				
				DashAST expr_node_x = (DashAST)domain_x.getChild(1).getChild(0);
				StringTemplate expr_x = exec(expr_node_x);
				String expr_id_x = Integer.toString(((DashAST)expr_node_x.getChild(0)).llvmResultID);
				
				int elementTypeIndex_x = 0;
				if (expr_node_x.evalType.getTypeIndex() == SymbolTable.tINTERVAL) {
					StringTemplate interval = stg.getInstanceOf("interval_to_vector");
					interval.setAttribute("id", DashAST.getUniqueId());
					interval.setAttribute("interval_var_expr", expr_x);
					interval.setAttribute("interval_var_expr_id", expr_id_x);
					
					expr_x = interval;
					expr_id_x = interval.getAttribute("id").toString();
					
					elementTypeIndex_x = SymbolTable.tINTEGER;
					
				} else {
					elementTypeIndex_x = ((VectorType) expr_node_x.evalType).elementType.getTypeIndex();
				}
				
				DashAST domain_y = (DashAST)t.getChild(1);
				
				// Get column
				DashAST sym_node_y = (DashAST)domain_y.getChild(0);
				Symbol sym_y = sym_node_y.symbol;
				int sym_id_y = sym_y.id;
				
				DashAST expr_node_y = (DashAST)domain_y.getChild(1).getChild(0);
				StringTemplate expr_y = exec(expr_node_y);
				String expr_id_y = Integer.toString(((DashAST)expr_node_y.getChild(0)).llvmResultID);
				
				int elementTypeIndex_y = 0;
				if (expr_node_y.evalType.getTypeIndex() == SymbolTable.tINTERVAL) {
					StringTemplate interval = stg.getInstanceOf("interval_to_vector");
					interval.setAttribute("id", DashAST.getUniqueId());
					interval.setAttribute("interval_var_expr", expr_y);
					interval.setAttribute("interval_var_expr_id", expr_id_y);
					
					expr_y = interval;
					expr_id_y = interval.getAttribute("id").toString();
					
					elementTypeIndex_y = SymbolTable.tINTEGER;
					
				} else {
					elementTypeIndex_y = ((VectorType) expr_node_y.evalType).elementType.getTypeIndex();
				}
				
				StringTemplate llvmType_y = stg.getInstanceOf(typeIndexToName.get(elementTypeIndex_y) + "_type");
				StringTemplate llvmType_x = stg.getInstanceOf(typeIndexToName.get(elementTypeIndex_x) + "_type");
				StringTemplate llvmType = stg.getInstanceOf(typeIndexToName.get(elementTypeIndex) + "_type");
				template.setAttribute("block_id", block_id);
				template.setAttribute("block", block);
				template.setAttribute("type_name", typeIndexToName.get(elementTypeIndex));
				template.setAttribute("llvm_type", llvmType);
				
				template.setAttribute("expr_y_id", expr_id_y);
				template.setAttribute("expr_y", expr_y);
				template.setAttribute("type_name_y", typeIndexToName.get(elementTypeIndex_y));
				template.setAttribute("llvm_type_y", llvmType_y);
				template.setAttribute("sym_id_y", sym_id_y);
				
				template.setAttribute("expr_x_id", expr_id_x);
				template.setAttribute("expr_x", expr_x);
				template.setAttribute("type_name_x", typeIndexToName.get(elementTypeIndex_x));
				template.setAttribute("llvm_type_x", llvmType_x);
				template.setAttribute("sym_id_x", sym_id_x);
				
				template.setAttribute("id", id);
			}
			
			return template;
		}
		
		case DashLexer.Break:
		{
			int id = ((DashAST)t).llvmResultID;
			
			Integer loop_id = loop_stack.peek();
			
			StringTemplate template = stg.getInstanceOf("break");
			
			template.setAttribute("loop_id", loop_id.intValue());
			template.setAttribute("id", id);
			
			return template;
		}

		case DashLexer.Continue:
		{
			int id = ((DashAST)t).llvmResultID;
			
			Integer loop_id = loop_stack.peek();
			
			StringTemplate template = stg.getInstanceOf("continue");
			
			template.setAttribute("loop_id", loop_id.intValue());
			template.setAttribute("id", id);
			return template;
		}
		
		case DashLexer.PRINT:
		{
			StringTemplate expr = exec((DashAST)t.getChild(0));
			
			Type type = ((DashAST)t.getChild(0)).evalType;
			int type_id = type.getTypeIndex();
			int arg_id = ((DashAST)t.getChild(0).getChild(0)).llvmResultID;
			int id = ((DashAST)t).llvmResultID;
			
			StringTemplate template = null;
			if (type_id == SymbolTable.tINTEGER) {
				template = stg.getInstanceOf("int_print");
			} else if (type_id == SymbolTable.tREAL) {
				template = stg.getInstanceOf("real_print");
			} else if (type_id == SymbolTable.tCHARACTER) {
				template = stg.getInstanceOf("char_print");
			} else if (type_id == SymbolTable.tBOOLEAN) {
				template = stg.getInstanceOf("bool_print");
			} else if (type_id == SymbolTable.tINTERVAL) {
				template = stg.getInstanceOf("interval_print");
			} else if (type_id == SymbolTable.tVECTOR) {
				template = stg.getInstanceOf("vector_print");
				int elementTypeIndex = ((VectorType) type).elementType.getTypeIndex();
				template.setAttribute("type_name", typeIndexToName.get(elementTypeIndex));
			} else if (type_id == SymbolTable.tMATRIX) {
				template = stg.getInstanceOf("matrix_print");
				int elementTypeIndex = ((MatrixType) type).elementType.getTypeIndex();
				template.setAttribute("type_name", typeIndexToName.get(elementTypeIndex));
			}
			
			template.setAttribute("expr", expr);
			template.setAttribute("expr_id", arg_id);
			template.setAttribute("id", id);
			
			return template;
		}
		
		case DashLexer.INPUT:
		{
			int id = ((DashAST)t).llvmResultID;
			DashAST node = (DashAST)t.getChild(0).getChild(0);
			
			if (node.getType() == DashLexer.DOT) {
				DashAST tupleNode = (DashAST) node.getChild(0);
				DashAST memberNode = (DashAST) node.getChild(1);
				
				VariableSymbol tuple = (VariableSymbol) tupleNode.symbol;
				TupleTypeSymbol tuple_type = (TupleTypeSymbol)tuple.type;
				int index = tuple_type.getMemberIndex(memberNode.getText());
				
				
				int type = node.evalType.getTypeIndex();
				
				StringTemplate template = null;
				if (type == SymbolTable.tINTEGER) {
					template = stg.getInstanceOf("int_input_tuple");
				}  else if (type == SymbolTable.tREAL) {
					template = stg.getInstanceOf("real_input_tuple");
				} else if (type == SymbolTable.tCHARACTER) {
					template = stg.getInstanceOf("char_input_tuple");
				} else if (type == SymbolTable.tBOOLEAN) {
					template = stg.getInstanceOf("bool_input_tuple");
				}
				
				template.setAttribute("index", index);
				template.setAttribute("tuple_type", tuple_type.tupleTypeIndex);
				template.setAttribute("tuple_id", tuple.id);
				template.setAttribute("id", id);
				return template;
			}
			
			Symbol sym = node.symbol;
			int sym_id = sym.id;
			
			StringTemplate template = null;
			if (sym.type.getTypeIndex() == SymbolTable.tINTEGER) {
				template = stg.getInstanceOf("int_input");
			} else if (sym.type.getTypeIndex() == SymbolTable.tREAL) {
				template = stg.getInstanceOf("real_input");
			} else if (sym.type.getTypeIndex() == SymbolTable.tCHARACTER) {
				template = stg.getInstanceOf("char_input");
			} else if (sym.type.getTypeIndex() == SymbolTable.tBOOLEAN) {
				template = stg.getInstanceOf("bool_input");
			}
			
			template.setAttribute("sym_id", sym_id);
			template.setAttribute("id", id);

			return template;
		}
		
		case DashLexer.UNPACK:
		{
			int id = ((DashAST)t).llvmResultID;
			int last_child_index = t.getChildCount() - 1;
			TupleTypeSymbol tuple = (TupleTypeSymbol) ((DashAST)t.getChild(last_child_index)).evalType;
			
			StringTemplate tuple_expr = exec((DashAST)t.getChild(last_child_index));
			int tuple_expr_id = ((DashAST)t.getChild(last_child_index).getChild(0)).llvmResultID;
			
			List<StringTemplate> element_assigns = new ArrayList<StringTemplate>();
			
			for (int i = 0; i < tuple.fields.size(); i++) {
				VariableSymbol member = (VariableSymbol) tuple.fields.get(i);
				VariableSymbol sym = (VariableSymbol) ((DashAST)t.getChild(i).getChild(0)).symbol;
				
				StringTemplate memberAssign = null;
				StringTemplate getMember = null;

				int field_type = member.type.getTypeIndex();
				if (field_type == SymbolTable.tINTEGER) {
					getMember = stg.getInstanceOf("int_get_tuple_member");
					memberAssign = stg.getInstanceOf("int_local_assign");
				}
				else if (field_type == SymbolTable.tREAL) {
					getMember = stg.getInstanceOf("real_get_tuple_member");
					memberAssign = stg.getInstanceOf("real_local_assign");
				}
				else if (field_type == SymbolTable.tCHARACTER) {
					getMember = stg.getInstanceOf("char_get_tuple_member");
					memberAssign = stg.getInstanceOf("char_local_assign");
				}
				else if (field_type == SymbolTable.tBOOLEAN) {
					getMember = stg.getInstanceOf("bool_get_tuple_member");
					memberAssign = stg.getInstanceOf("bool_local_assign");
				}
				
				int uid = DashAST.getUniqueId();
				getMember.setAttribute("id", uid);
				getMember.setAttribute("tuple_expr_id", tuple_expr_id);
				getMember.setAttribute("tuple_type", tuple.tupleTypeIndex);
				getMember.setAttribute("index", i);
				
				memberAssign.setAttribute("expr_id", uid);
				memberAssign.setAttribute("expr", getMember);
				memberAssign.setAttribute("sym_id", sym.id);
				memberAssign.setAttribute("id", DashAST.getUniqueId());
				
				element_assigns.add(memberAssign);
			}
			
			StringTemplate template = stg.getInstanceOf("tuple_unpack");;
			template.setAttribute("tuple_expr", tuple_expr);
			template.setAttribute("element_assigns", element_assigns);
			template.setAttribute("id", id);
			
			return template;
		}
			
		case DashLexer.VAR_DECL:
		{
			Symbol sym = ((DashAST)t.getChild(1)).symbol;
			Scope scope = sym.scope;
			int sym_id = sym.id;
			
			//this.global.put(sym_id, sym);
			
			int id = ((DashAST)t).llvmResultID;
			int type = sym.type.getTypeIndex();
			DashAST child = (DashAST)t.getChild(2);
			if (child != null) {
				StringTemplate expr = exec(child);
				int expr_id = ((DashAST)child.getChild(0)).llvmResultID;
				
				StringTemplate template = null;
				if (type == SymbolTable.tTUPLE) {
					return assignTuple(id, (VariableSymbol)sym, expr_id, expr);
				} else if (type == SymbolTable.tINTERVAL) {
					return assignInterval(id, true, (VariableSymbol)sym, expr_id, expr);
				} else if (type == SymbolTable.tVECTOR) {
					return assignVector(t);
				} else if (type == SymbolTable.tMATRIX) {
					return assignMatrix(t);
				}
				
				if (scope.getScopeIndex() == SymbolTable.scGLOBAL) {
					if (type == SymbolTable.tINTEGER) {
						template = stg.getInstanceOf("int_global_assign");
					} else if (type == SymbolTable.tREAL) {
						template = stg.getInstanceOf("real_global_assign");
					} else if (type == SymbolTable.tCHARACTER) {
						template = stg.getInstanceOf("char_global_assign");
					} else if (type == SymbolTable.tBOOLEAN) {
						template = stg.getInstanceOf("bool_global_assign");
					}
				} else {
					if (type == SymbolTable.tINTEGER) {
						template = stg.getInstanceOf("int_local_assign");
					} else if (type == SymbolTable.tREAL) {
						template = stg.getInstanceOf("real_local_assign");
					} else if (type == SymbolTable.tCHARACTER) {
						template = stg.getInstanceOf("char_local_assign");
					} else if (type == SymbolTable.tBOOLEAN) {
						template = stg.getInstanceOf("bool_local_assign");
					}
				}
				
				template.setAttribute("expr_id", expr_id);
				template.setAttribute("expr", expr);
				template.setAttribute("sym_id", sym_id);
				template.setAttribute("id", id);
				return template;
			}
			
			return new StringTemplate("");
		}

		case DashLexer.ASSIGN:
		{
			int id = ((DashAST)t).llvmResultID;
			DashAST node = (DashAST)t.getChild(0).getChild(0);
			
			if (node.getType() == DashLexer.DOT) {
				DashAST tupleNode = (DashAST) node.getChild(0);
				DashAST memberNode = (DashAST) node.getChild(1);
				
				VariableSymbol tuple = (VariableSymbol) tupleNode.symbol;
				TupleTypeSymbol tuple_type = (TupleTypeSymbol)tuple.type;
				int index = tuple_type.getMemberIndex(memberNode.getText());
				
				StringTemplate expr = exec((DashAST)t.getChild(1));
				int arg_id = ((DashAST)t.getChild(1).getChild(0)).llvmResultID;
				
				int type = node.evalType.getTypeIndex();
				
				StringTemplate template = null;
				if (type == SymbolTable.tINTEGER) {
					template = stg.getInstanceOf("int_tuple_assign");
				}  else if (type == SymbolTable.tREAL) {
					template = stg.getInstanceOf("real_tuple_assign");
				} else if (type == SymbolTable.tCHARACTER) {
					template = stg.getInstanceOf("char_tuple_assign");
				} else if (type == SymbolTable.tBOOLEAN) {
					template = stg.getInstanceOf("bool_tuple_assign");
				}
				
				StringTemplate getLocalTuple = stg.getInstanceOf("tuple_get_local"); /* scope? */
				getLocalTuple.setAttribute("id", DashAST.getUniqueId());
				getLocalTuple.setAttribute("sym_id", tuple.id);
				getLocalTuple.setAttribute("type_id", tuple_type.tupleTypeIndex);
				
				template.setAttribute("expr_id", arg_id);
				template.setAttribute("expr", expr);
				template.setAttribute("index", index);
				template.setAttribute("tuple_type", tuple_type.tupleTypeIndex);
				template.setAttribute("tuple_expr", getLocalTuple);
				template.setAttribute("tuple_expr_id", getLocalTuple.getAttribute("id"));
				template.setAttribute("id", id);
				return template;
			} else if (node.getType() == DashLexer.VECTOR_INDEX) {
				DashAST varNode = (DashAST) node.getChild(0);
				DashAST indexNode = (DashAST) node.getChild(1);

				VectorType vecType = (VectorType) varNode.evalType;
				VariableSymbol varSymbol = (VariableSymbol) varNode.symbol;
				int elementTypeIndex = vecType.elementType.getTypeIndex();

				StringTemplate getVector = stg.getInstanceOf("vector_get_local");
				getVector.setAttribute("id", DashAST.getUniqueId());
				getVector.setAttribute("sym_id", varSymbol.id);
				
				int exprTypeIndex = ((DashAST)t.getChild(1)).evalType.getTypeIndex();
				StringTemplate template = null;
				if (indexNode.evalType.getTypeIndex() == SymbolTable.tINTERVAL ||
						indexNode.evalType.getTypeIndex() == SymbolTable.tVECTOR) {
					if (exprTypeIndex ==  SymbolTable.tINTERVAL ||
							exprTypeIndex ==  SymbolTable.tVECTOR) {
						template = stg.getInstanceOf("vector_index_assign_vector");
					} else {
						template = stg.getInstanceOf("vector_index_assign_scalar");
					}
				}
				else
					template = stg.getInstanceOf("vector_elem_assign");
				
				template.setAttribute("id", id);
				template.setAttribute("vector_expr", getVector);
				template.setAttribute("vector_expr_id", getVector.getAttribute("id"));

				StringTemplate llvmType = stg.getInstanceOf(typeIndexToName.get(elementTypeIndex) + "_type");
				template.setAttribute("llvm_type", llvmType);
				template.setAttribute("type_name", typeIndexToName.get(elementTypeIndex));

				StringTemplate indexExpr = exec(indexNode);
				
				if (indexNode.evalType.getTypeIndex() == SymbolTable.tINTERVAL) {
					StringTemplate interval = stg.getInstanceOf("interval_to_vector");
					interval.setAttribute("id", DashAST.getUniqueId());
					interval.setAttribute("interval_var_expr", indexExpr);
					interval.setAttribute("interval_var_expr_id", indexExpr.getAttribute("id"));
					
					indexExpr = interval;
				}
				
				template.setAttribute("index_expr", indexExpr);
				template.setAttribute("index_expr_id", indexExpr.getAttribute("id"));

				StringTemplate valueExpr = exec((DashAST)t.getChild(1));
				
				if (exprTypeIndex ==  SymbolTable.tINTERVAL) {
					StringTemplate interval = stg.getInstanceOf("interval_to_vector");
					interval.setAttribute("id", DashAST.getUniqueId());
					interval.setAttribute("interval_var_expr", valueExpr);
					interval.setAttribute("interval_var_expr_id", valueExpr.getAttribute("id"));
					
					valueExpr = interval;
				}
				
				template.setAttribute("expr", valueExpr);
				template.setAttribute("expr_id", valueExpr.getAttribute("id"));

				return template;
			} else if (node.getType() == DashLexer.MATRIX_INDEX) {
				DashAST varNode = (DashAST) node.getChild(0);
				DashAST rowIndexNode = (DashAST) node.getChild(1);
				DashAST columnIndexNode = (DashAST) node.getChild(2);

				MatrixType matType = (MatrixType) varNode.evalType;
				VariableSymbol varSymbol = (VariableSymbol) varNode.symbol;
				int elementTypeIndex = matType.elementType.getTypeIndex();

				StringTemplate getMatrix = stg.getInstanceOf("matrix_get_local");
				getMatrix.setAttribute("id", DashAST.getUniqueId());
				getMatrix.setAttribute("sym_id", varSymbol.id);
				
				Type exprType = ((DashAST)t.getChild(1)).evalType;
				int exprTypeIndex = exprType.getTypeIndex();
				
				StringTemplate template = null;
				if (rowIndexNode.evalType.getTypeIndex() == SymbolTable.tINTEGER &&
						columnIndexNode.evalType.getTypeIndex() == SymbolTable.tINTEGER) {
					template = stg.getInstanceOf("matrix_elem_assign");
				} else if (rowIndexNode.evalType.getTypeIndex() == SymbolTable.tINTEGER &&
						(columnIndexNode.evalType.getTypeIndex() == SymbolTable.tINTERVAL ||
						columnIndexNode.evalType.getTypeIndex() == SymbolTable.tVECTOR)) {
					if (exprTypeIndex ==  SymbolTable.tINTERVAL ||
							exprTypeIndex ==  SymbolTable.tVECTOR) {
						template = stg.getInstanceOf("matrix_row_assign");
					} else {
						template = stg.getInstanceOf("matrix_row_assign_scalar");
					}
				} else if (columnIndexNode.evalType.getTypeIndex() == SymbolTable.tINTEGER &&
						(rowIndexNode.evalType.getTypeIndex() == SymbolTable.tINTERVAL ||
						rowIndexNode.evalType.getTypeIndex() == SymbolTable.tVECTOR)) {
					if (exprTypeIndex ==  SymbolTable.tINTERVAL ||
							exprTypeIndex ==  SymbolTable.tVECTOR) {
						template = stg.getInstanceOf("matrix_column_assign");
					} else {
						template = stg.getInstanceOf("matrix_column_assign_scalar");
					}
				} else if ((rowIndexNode.evalType.getTypeIndex() == SymbolTable.tINTERVAL ||
						rowIndexNode.evalType.getTypeIndex() == SymbolTable.tVECTOR) &&
						(columnIndexNode.evalType.getTypeIndex() == SymbolTable.tINTERVAL ||
						columnIndexNode.evalType.getTypeIndex() == SymbolTable.tVECTOR)) {
					if (exprTypeIndex ==  SymbolTable.tMATRIX) {
						template = stg.getInstanceOf("matrix_index_assign_matrix");
					} else {
						template = stg.getInstanceOf("matrix_index_assign_scalar");
					}
				}
				
				StringTemplate rowIndexExpr = exec(rowIndexNode);
				
				if (rowIndexNode.evalType.getTypeIndex() == SymbolTable.tINTERVAL) {
					StringTemplate interval = stg.getInstanceOf("interval_to_vector");
					interval.setAttribute("id", DashAST.getUniqueId());
					interval.setAttribute("interval_var_expr", rowIndexExpr);
					interval.setAttribute("interval_var_expr_id", rowIndexExpr.getAttribute("id"));
					
					rowIndexExpr = interval;
				}
				
				StringTemplate columnIndexExpr = exec(columnIndexNode);
				
				if (columnIndexNode.evalType.getTypeIndex() == SymbolTable.tINTERVAL) {
					StringTemplate interval = stg.getInstanceOf("interval_to_vector");
					interval.setAttribute("id", DashAST.getUniqueId());
					interval.setAttribute("interval_var_expr", columnIndexExpr);
					interval.setAttribute("interval_var_expr_id", columnIndexExpr.getAttribute("id"));
					
					columnIndexExpr = interval;
				}
			
				StringTemplate valueExpr = exec((DashAST)t.getChild(1));
				boolean promoteVector = false;
				if (exprTypeIndex ==  SymbolTable.tINTERVAL) {
					StringTemplate interval = stg.getInstanceOf("interval_to_vector");
					interval.setAttribute("id", DashAST.getUniqueId());
					interval.setAttribute("interval_var_expr", valueExpr);
					interval.setAttribute("interval_var_expr_id", valueExpr.getAttribute("id"));
					
					valueExpr = interval;
					
					if (elementTypeIndex == SymbolTable.tREAL)
						promoteVector = true;
				} else if (elementTypeIndex == SymbolTable.tREAL &&
						exprTypeIndex ==  SymbolTable.tVECTOR) {
					VectorType vType = (VectorType) exprType;
					
					if (vType.elementType.getTypeIndex() == SymbolTable.tINTEGER) {
						promoteVector = true;
					}
				}
				
				if (promoteVector) {
					StringTemplate promote = stg.getInstanceOf("vector_to_real");
					promote.setAttribute("id", DashAST.getUniqueId());
					promote.setAttribute("type_name", typeIndexToName.get(SymbolTable.tINTEGER));
					promote.setAttribute("vector_var_expr", valueExpr);
					promote.setAttribute("vector_var_expr_id", valueExpr.getAttribute("id"));
					
					valueExpr = promote;
				}
				
				if (elementTypeIndex == SymbolTable.tREAL &&
						exprTypeIndex == SymbolTable.tINTEGER) {
					//int_to_real(id, expr, expr_id)
					StringTemplate promote = stg.getInstanceOf("int_to_real");
					promote.setAttribute("id", DashAST.getUniqueId());
					promote.setAttribute("expr", valueExpr);
					promote.setAttribute("expr_id", valueExpr.getAttribute("id"));
					
					valueExpr = promote;
				}
				
				StringTemplate llvmType = stg.getInstanceOf(typeIndexToName.get(elementTypeIndex) + "_type");
				
				template.setAttribute("id", id);
				template.setAttribute("llvm_type", llvmType);
				template.setAttribute("type_name", typeIndexToName.get(elementTypeIndex));
				template.setAttribute("matrix_expr", getMatrix);
				template.setAttribute("matrix_expr_id", getMatrix.getAttribute("id"));
				template.setAttribute("row_expr", rowIndexExpr);
				template.setAttribute("row_expr_id", rowIndexExpr.getAttribute("id"));
				template.setAttribute("column_expr", columnIndexExpr);
				template.setAttribute("column_expr_id", columnIndexExpr.getAttribute("id"));
				template.setAttribute("expr", valueExpr);
				template.setAttribute("expr_id", valueExpr.getAttribute("id"));

				return template;
			}

			Symbol sym = node.symbol;
			int sym_id = sym.id;
			
			int type = ((DashAST)t.getChild(0)).evalType.getTypeIndex();
			
			StringTemplate expr = exec((DashAST)t.getChild(1));
			int arg_id = ((DashAST)t.getChild(1).getChild(0)).llvmResultID;
			
			StringTemplate template = null;
			if (type == SymbolTable.tINTEGER) {
				template = stg.getInstanceOf("int_local_assign");
			} else if (type == SymbolTable.tREAL) {
				template = stg.getInstanceOf("real_local_assign");
			} else if (type == SymbolTable.tCHARACTER) {
				template = stg.getInstanceOf("char_local_assign");
			} else if (type == SymbolTable.tBOOLEAN) {
				template = stg.getInstanceOf("bool_local_assign");
			} else if (type == SymbolTable.tTUPLE) {
				return assignTuple(id, (VariableSymbol)sym, arg_id, expr);
			} else if (type == SymbolTable.tINTERVAL) {
				return assignInterval(id, false, (VariableSymbol)sym, arg_id, expr);
			}  else if (type == SymbolTable.tVECTOR) {
				return assignVector(t);
			} else if (type == SymbolTable.tMATRIX) {
				return assignMatrix(t);
			}
			
			template.setAttribute("expr_id", arg_id);
			template.setAttribute("expr", expr);
			template.setAttribute("sym_id", sym_id);
			template.setAttribute("id", id);
			return template;
		}
		
		case DashLexer.UNARY_MINUS:
		{
			String id = Integer.toString(((DashAST)t).llvmResultID);
			Type type = ((DashAST)t.getChild(0)).evalType;
			int type_index = type.getTypeIndex();
			
			StringTemplate expr = exec((DashAST)t.getChild(0));
			String expr_id = Integer.toString(((DashAST)t.getChild(0)).llvmResultID);
			
			StringTemplate template = null;
			if (type_index == SymbolTable.tINTERVAL) {
				template = stg.getInstanceOf("interval_minus");
			} else if (type_index == SymbolTable.tVECTOR) {
				template = stg.getInstanceOf("vector_minus");
				int elementTypeIndex = ((VectorType) type).elementType.getTypeIndex();
				template.setAttribute("type_name", typeIndexToName.get(elementTypeIndex));
			} else if (type_index == SymbolTable.tMATRIX) {
				template = stg.getInstanceOf("matrix_minus");
				int elementTypeIndex = ((MatrixType) type).elementType.getTypeIndex();
				template.setAttribute("type_name", typeIndexToName.get(elementTypeIndex));
			} else if (type_index == SymbolTable.tINTEGER) {
				template = stg.getInstanceOf("int_minus");
			} else if (type_index == SymbolTable.tREAL) {
				template = stg.getInstanceOf("real_minus");
			}
			
			template.setAttribute("expr_id", expr_id);
			template.setAttribute("expr", expr);
			template.setAttribute("id", id);
			
			return template;
		}
		
		case DashLexer.Not:
		{
			String id = Integer.toString(((DashAST)t).llvmResultID);
			Type type = ((DashAST)t.getChild(0)).evalType;
			int type_index = type.getTypeIndex();
			
			StringTemplate expr = exec((DashAST)t.getChild(0));
			String expr_id = Integer.toString(((DashAST)t.getChild(0)).llvmResultID);
			
			StringTemplate template = null;
			if (type_index == SymbolTable.tVECTOR) {
				template = stg.getInstanceOf("vector_not");
				int elementTypeIndex = ((VectorType) type).elementType.getTypeIndex();
				template.setAttribute("type_name", typeIndexToName.get(elementTypeIndex));
			} else if (type_index == SymbolTable.tMATRIX) {
				template = stg.getInstanceOf("matrix_not");
				int elementTypeIndex = ((MatrixType) type).elementType.getTypeIndex();
				template.setAttribute("type_name", typeIndexToName.get(elementTypeIndex));
			} else if (type_index == SymbolTable.tBOOLEAN) {
				template = stg.getInstanceOf("bool_not");
			}
			
			template.setAttribute("expr_id", expr_id);
			template.setAttribute("expr", expr);
			template.setAttribute("id", id);
			
			return template;
		}
		
		case DashLexer.And:
			return operation(t, LLVMOps.AND);
			
		case DashLexer.Or:
			return operation(t, LLVMOps.OR);
			
		case DashLexer.Xor:
			return operation(t, LLVMOps.XOR);

		case DashLexer.EQUALITY:
			return comparisonPrimaryOrTuple(t, LLVMOps.EQ);

		case DashLexer.INEQUALITY:
			return comparisonPrimaryOrTuple(t, LLVMOps.NE);

		case DashLexer.LESS:
			return operation(t, LLVMOps.LT);
			
		case DashLexer.LESS_EQUAL:
			return operation(t, LLVMOps.LE);

		case DashLexer.GREATER:
			return operation(t, LLVMOps.GT);
			
		case DashLexer.GREATER_EQUAL:
			return operation(t, LLVMOps.GE);

		case DashLexer.ADD:
			return operation(t, LLVMOps.ADD);

		case DashLexer.SUBTRACT:
			return operation(t, LLVMOps.SUB);

		case DashLexer.MULTIPLY:
			return operation(t, LLVMOps.MULT);

		case DashLexer.DIVIDE:
			return operation(t, LLVMOps.DIV);
			
		case DashLexer.MODULAR:
			return operation(t, LLVMOps.MOD);
			
		case DashLexer.POWER:
			return operation(t, LLVMOps.POWER);
			
		case DashLexer.CONCAT:
			return operation(t, LLVMOps.CONCAT);
		
		case DashLexer.DOTPRODUCT:
			return operation(t, LLVMOps.DOTPRODUCT);
			
		case DashLexer.RANGE:
		{
			String id = Integer.toString(((DashAST)t).llvmResultID);
			
			if (((DashAST)t.getChild(0)).promoteToType != null) {
				// TODO
			}
			
			StringTemplate lhs = exec((DashAST)t.getChild(0));
			String lhs_id = Integer.toString(((DashAST)t.getChild(0)).llvmResultID);
			
			StringTemplate rhs = exec((DashAST)t.getChild(1));
			String rhs_id = Integer.toString(((DashAST)t.getChild(1)).llvmResultID);
			
			StringTemplate template = stg.getInstanceOf("interval_init_literal");
			
			//interval_init_literal(id, lhs_expr, lhs_expr_id, rhs_expr, rhs_expr_id)
			template.setAttribute("rhs_expr_id", rhs_id);
			template.setAttribute("rhs_expr", rhs);
			template.setAttribute("lhs_expr_id", lhs_id);
			template.setAttribute("lhs_expr", lhs);
			template.setAttribute("id", id);
			
			return template;
		}
		
		case DashLexer.By:
		{
			String id = Integer.toString(((DashAST)t).llvmResultID);
			
			if (((DashAST)t.getChild(0)).promoteToType != null) {
				// TODO
			}
			
			StringTemplate lhs = exec((DashAST)t.getChild(0));
			String lhs_id = Integer.toString(((DashAST)t.getChild(0)).llvmResultID);
			
			StringTemplate rhs = exec((DashAST)t.getChild(1));
			String rhs_id = Integer.toString(((DashAST)t.getChild(1)).llvmResultID);
			
			StringTemplate template = null;
			
			if (((DashAST)t.getChild(0)).evalType.getTypeIndex() == SymbolTable.tINTERVAL)
				template = stg.getInstanceOf("interval_by");
			else {
				VectorType vType = (VectorType)((DashAST)t.getChild(0)).evalType;
				int elementTypeIndex = vType.elementType.getTypeIndex();
				
				template = stg.getInstanceOf("vector_by");
				template.setAttribute("type_name", typeIndexToName.get(elementTypeIndex));
			}
			
			//interval_by(id, lhs_expr, lhs_expr_id, rhs_expr, rhs_expr_id)
			template.setAttribute("rhs_expr_id", rhs_id);
			template.setAttribute("rhs_expr", rhs);
			template.setAttribute("lhs_expr_id", lhs_id);
			template.setAttribute("lhs_expr", lhs);
			template.setAttribute("id", id);
			
			return template;
		}
			
		case DashLexer.DOT:
		{
			int id = ((DashAST)t).llvmResultID;
			DashAST tupleNode = (DashAST) t.getChild(0);
			DashAST memberNode = (DashAST) t.getChild(1);
			
			StringTemplate tuple_expr = exec(tupleNode);
			int tuple_expr_id = tupleNode.llvmResultID;
			
			VariableSymbol tuple = (VariableSymbol) tupleNode.symbol;
			TupleTypeSymbol tuple_type = null;
			if (tuple != null) {
				tuple_type = (TupleTypeSymbol)tuple.type;
			} else {
				tuple_type = (TupleTypeSymbol)tupleNode.evalType;
			}
			
			int index = tuple_type.getMemberIndex(memberNode.getText());
			
			int type = t.evalType.getTypeIndex();
			
			StringTemplate template = null;
			if (type == SymbolTable.tINTEGER) {
				//*_get_tuple_member(id, tuple_expr, tuple_expr_id, tuple_type, index)
				template = stg.getInstanceOf("int_get_tuple_member");
			}  else if (type == SymbolTable.tREAL) {
				template = stg.getInstanceOf("real_get_tuple_member");
			} else if (type == SymbolTable.tCHARACTER) {
				template = stg.getInstanceOf("char_get_tuple_member");
			} else if (type == SymbolTable.tBOOLEAN) {
				template = stg.getInstanceOf("bool_get_tuple_member");
			}
			
			template.setAttribute("index", index);
			template.setAttribute("tuple_type", tuple_type.tupleTypeIndex);
			template.setAttribute("tuple_expr_id", tuple_expr_id);
			template.setAttribute("tuple_expr", tuple_expr);
			template.setAttribute("id", id);
			return template;
		}

		case DashLexer.VECTOR_INDEX:
		{
			DashAST varNode = (DashAST) t.getChild(0);
			DashAST indexNode = (DashAST) t.getChild(1);
			
			int elementTypeIndex = -1;
			if (varNode.evalType.getTypeIndex() == SymbolTable.tINTERVAL) {
				elementTypeIndex = SymbolTable.tINTEGER;
			} else {
				VectorType vecType = (VectorType) varNode.evalType;
				elementTypeIndex = vecType.elementType.getTypeIndex();
			}
			
			StringTemplate getVector = exec((DashAST) varNode);
			
			if (varNode.evalType.getTypeIndex() == SymbolTable.tINTERVAL) {
				//interval_to_vector(id, interval_var_expr, interval_var_expr_id)
				StringTemplate interval = stg.getInstanceOf("interval_to_vector");
				interval.setAttribute("id", DashAST.getUniqueId());
				interval.setAttribute("interval_var_expr", getVector);
				interval.setAttribute("interval_var_expr_id", getVector.getAttribute("id"));
				
				getVector = interval;
			}

			if (indexNode.evalType.getTypeIndex() == SymbolTable.tINTEGER) {
				StringTemplate template = stg.getInstanceOf("vector_get_element");
				template.setAttribute("id", t.llvmResultID);
				template.setAttribute("vector_expr", getVector);
				template.setAttribute("vector_expr_id", getVector.getAttribute("id"));

				StringTemplate llvmType = stg.getInstanceOf(typeIndexToName.get(elementTypeIndex) + "_type");
				template.setAttribute("llvm_type", llvmType);
				template.setAttribute("type_name", typeIndexToName.get(elementTypeIndex));

				StringTemplate indexExpr = exec(indexNode);
				template.setAttribute("index_expr", indexExpr);
				template.setAttribute("index_expr_id", indexExpr.getAttribute("id"));

				return template;
			} else if (indexNode.evalType.getTypeIndex() == SymbolTable.tINTERVAL ||
					indexNode.evalType.getTypeIndex() == SymbolTable.tVECTOR) {
				StringTemplate indexExpr = exec(indexNode);
				
				if (indexNode.evalType.getTypeIndex() == SymbolTable.tINTERVAL) {
					//interval_to_vector(id, interval_var_expr, interval_var_expr_id)
					StringTemplate interval = stg.getInstanceOf("interval_to_vector");
					interval.setAttribute("id", DashAST.getUniqueId());
					interval.setAttribute("interval_var_expr", indexExpr);
					interval.setAttribute("interval_var_expr_id", indexExpr.getAttribute("id"));
					
					indexExpr = interval;
				}
				
				// vector_index(id, type_name, vector_expr, vector_expr_id, index_expr, index_expr_id) 
				StringTemplate template = stg.getInstanceOf("vector_index");
				template.setAttribute("id", t.llvmResultID);
				template.setAttribute("vector_expr", getVector);
				template.setAttribute("vector_expr_id", getVector.getAttribute("id"));
				template.setAttribute("type_name", typeIndexToName.get(elementTypeIndex));
				template.setAttribute("index_expr", indexExpr);
				template.setAttribute("index_expr_id", indexExpr.getAttribute("id"));
				
				return template;
			}
		}
		
		case DashLexer.MATRIX_INDEX:
		{
			DashAST varNode = (DashAST) t.getChild(0);
			DashAST rowIndexNode = (DashAST) t.getChild(1);
			DashAST columnIndexNode = (DashAST) t.getChild(2);

			MatrixType matType = (MatrixType) varNode.evalType;
			int elementTypeIndex = matType.elementType.getTypeIndex();

			StringTemplate getMatrix = exec((DashAST) varNode);;
			StringTemplate rowExpr = exec(rowIndexNode);
			StringTemplate columnExpr = exec(columnIndexNode);
			
			StringTemplate template = null;
			if (rowIndexNode.evalType.getTypeIndex() == SymbolTable.tINTEGER &&
					columnIndexNode.evalType.getTypeIndex() == SymbolTable.tINTEGER) {
				template = stg.getInstanceOf("matrix_get_element");
			} else if (rowIndexNode.evalType.getTypeIndex() == SymbolTable.tINTEGER &&
					(columnIndexNode.evalType.getTypeIndex() == SymbolTable.tINTERVAL ||
					columnIndexNode.evalType.getTypeIndex() == SymbolTable.tVECTOR)) {
				template = stg.getInstanceOf("matrix_row_index");
			} else if (columnIndexNode.evalType.getTypeIndex() == SymbolTable.tINTEGER &&
					(rowIndexNode.evalType.getTypeIndex() == SymbolTable.tINTERVAL ||
					rowIndexNode.evalType.getTypeIndex() == SymbolTable.tVECTOR)) {
				template = stg.getInstanceOf("matrix_column_index");
			} else if ((rowIndexNode.evalType.getTypeIndex() == SymbolTable.tINTERVAL ||
					rowIndexNode.evalType.getTypeIndex() == SymbolTable.tVECTOR) &&
					(columnIndexNode.evalType.getTypeIndex() == SymbolTable.tINTERVAL ||
					columnIndexNode.evalType.getTypeIndex() == SymbolTable.tVECTOR)) {
				template = stg.getInstanceOf("matrix_index");
			}
			
			// Promote interval
			if  (rowIndexNode.evalType.getTypeIndex() == SymbolTable.tINTERVAL) {
				//interval_to_vector(id, interval_var_expr, interval_var_expr_id)
				StringTemplate interval = stg.getInstanceOf("interval_to_vector");
				interval.setAttribute("id", DashAST.getUniqueId());
				interval.setAttribute("interval_var_expr", rowExpr);
				interval.setAttribute("interval_var_expr_id", rowExpr.getAttribute("id"));
				
				rowExpr = interval;
			}
			
			// Promote interval
			if  (columnIndexNode.evalType.getTypeIndex() == SymbolTable.tINTERVAL) {
				//interval_to_vector(id, interval_var_expr, interval_var_expr_id)
				StringTemplate interval = stg.getInstanceOf("interval_to_vector");
				interval.setAttribute("id", DashAST.getUniqueId());
				interval.setAttribute("interval_var_expr", columnExpr);
				interval.setAttribute("interval_var_expr_id", columnExpr.getAttribute("id"));
				
				columnExpr = interval;
			}
			
			template.setAttribute("id", t.llvmResultID);
			template.setAttribute("matrix_expr", getMatrix);
			template.setAttribute("matrix_expr_id", getMatrix.getAttribute("id"));

			StringTemplate llvmType = stg.getInstanceOf(typeIndexToName.get(elementTypeIndex) + "_type");
			template.setAttribute("llvm_type", llvmType);
			template.setAttribute("type_name", typeIndexToName.get(elementTypeIndex));
			template.setAttribute("row_expr", rowExpr);
			template.setAttribute("row_expr_id", rowExpr.getAttribute("id"));
			template.setAttribute("column_expr", columnExpr);
			template.setAttribute("column_expr_id", columnExpr.getAttribute("id"));

			return template;
		}
		
		case DashLexer.TYPECAST:
		{
			int id = ((DashAST)t).llvmResultID;
			DashAST child = (DashAST)t.getChild(0);
			
			Type castTo = t.evalType;
			Type castFrom = child.evalType;
			
			StringTemplate expr = exec(child);
			int expr_id = ((DashAST)child.getChild(0)).llvmResultID;
			
			StringTemplate template = null;
			
			//TODO
			if (castTo.getTypeIndex() == SymbolTable.tTUPLE) {
				template = stg.getInstanceOf("tuple_assign");
				List<StringTemplate> element_assigns = new ArrayList<StringTemplate>();

				TupleTypeSymbol to_tuple_type = (TupleTypeSymbol) castTo;
				TupleTypeSymbol from_tuple_type = (TupleTypeSymbol) castFrom;
				
				List<Symbol> fields_to = to_tuple_type.fields;
				List<Symbol> fields_from = from_tuple_type.fields;
				for (int i = 0; i < fields_to.size(); i++) {
					StringTemplate memberAssign = null;
					StringTemplate getMember = null;
					
					int field_type_to = fields_to.get(i).type.getTypeIndex();
					if (field_type_to == SymbolTable.tINTEGER) {
						memberAssign = stg.getInstanceOf("int_tuple_assign");
					} else if (field_type_to == SymbolTable.tREAL) {
						memberAssign = stg.getInstanceOf("real_tuple_assign");
					} else if (field_type_to== SymbolTable.tCHARACTER) {
						memberAssign = stg.getInstanceOf("char_tuple_assign");
					} else if (field_type_to == SymbolTable.tBOOLEAN) {
						memberAssign = stg.getInstanceOf("bool_tuple_assign");
					}

					int field_type_from = fields_from.get(i).type.getTypeIndex();
					if (field_type_from == SymbolTable.tINTEGER) {
						getMember = stg.getInstanceOf("int_get_tuple_member");
					} else if (field_type_from == SymbolTable.tREAL) {
						getMember = stg.getInstanceOf("real_get_tuple_member");
					} else if (field_type_from== SymbolTable.tCHARACTER) {
						getMember = stg.getInstanceOf("char_get_tuple_member");
					} else if (field_type_from == SymbolTable.tBOOLEAN) {
						getMember = stg.getInstanceOf("bool_get_tuple_member");
					}

					StringTemplate cast = null;
					if (field_type_from == SymbolTable.tBOOLEAN) {
						if (field_type_to == SymbolTable.tBOOLEAN) {
							cast = stg.getInstanceOf("bool_to_bool");
						} else if (field_type_to == SymbolTable.tCHARACTER) {
							cast = stg.getInstanceOf("bool_to_char");
						} else if (field_type_to == SymbolTable.tINTEGER) {
							cast = stg.getInstanceOf("bool_to_int");
						} else if (field_type_to == SymbolTable.tREAL) {
							cast = stg.getInstanceOf("bool_to_real");
						}
					} else if (field_type_from == SymbolTable.tCHARACTER) {
						if (field_type_to == SymbolTable.tBOOLEAN) {
							cast = stg.getInstanceOf("char_to_bool");
						} else if (field_type_to == SymbolTable.tCHARACTER) {
							cast = stg.getInstanceOf("char_to_char");
						} else if (field_type_to == SymbolTable.tINTEGER) {
							cast = stg.getInstanceOf("char_to_int");
						} else if (field_type_to == SymbolTable.tREAL) {
							cast = stg.getInstanceOf("char_to_real");
						}
					} else if (field_type_from == SymbolTable.tINTEGER) {
						if (field_type_to == SymbolTable.tBOOLEAN) {
							cast = stg.getInstanceOf("int_to_bool");
						} else if (field_type_to == SymbolTable.tCHARACTER) {
							cast = stg.getInstanceOf("int_to_char");
						} else if (field_type_to == SymbolTable.tINTEGER) {
							cast = stg.getInstanceOf("int_to_int");
						} else if (field_type_to == SymbolTable.tREAL) {
							cast = stg.getInstanceOf("int_to_real");
						}
					} else if (field_type_from == SymbolTable.tREAL) {
						if (field_type_to == SymbolTable.tINTEGER) {
							cast = stg.getInstanceOf("real_to_int");
						} else if (field_type_to == SymbolTable.tREAL) {
							cast = stg.getInstanceOf("real_to_real");
						}
					}
					
					int uid1 = DashAST.getUniqueId();
					int uid2 = DashAST.getUniqueId();
					getMember.setAttribute("id", uid1);
					getMember.setAttribute("tuple_expr_id", expr_id);
					getMember.setAttribute("tuple_type", from_tuple_type.tupleTypeIndex);
					getMember.setAttribute("index", i);
					
					//(id, expr, expr_id) 
					cast.setAttribute("id", uid2 + "_cast");
					cast.setAttribute("expr", getMember);
					cast.setAttribute("expr_id", uid1);

					memberAssign.setAttribute("id", uid2);
					memberAssign.setAttribute("tuple_expr_id", id);
					memberAssign.setAttribute("tuple_type", to_tuple_type.tupleTypeIndex);
					memberAssign.setAttribute("index", i);
					memberAssign.setAttribute("expr", cast);
					memberAssign.setAttribute("expr_id", uid2 + "_cast");

					element_assigns.add(memberAssign);
				}
				//tuple_init_literal(id, type_id, element_exprs)
				StringTemplate createTuple = stg.getInstanceOf("tuple_init_literal");
				createTuple.setAttribute("type_id", to_tuple_type.tupleTypeIndex);
				createTuple.setAttribute("id", id);
				
				template.setAttribute("lhs_expr", createTuple);
				template.setAttribute("rhs_expr", expr);
				template.setAttribute("element_assigns", element_assigns);
				template.setAttribute("id", id);
				
				return template;
			}
			
			if (castFrom.getTypeIndex() == SymbolTable.tBOOLEAN) {
				if (castTo.getTypeIndex() == SymbolTable.tBOOLEAN) {
					template = stg.getInstanceOf("bool_to_bool");
				} else if (castTo.getTypeIndex() == SymbolTable.tCHARACTER) {
					template = stg.getInstanceOf("bool_to_char");
				} else if (castTo.getTypeIndex() == SymbolTable.tINTEGER) {
					template = stg.getInstanceOf("bool_to_int");
				} else if (castTo.getTypeIndex() == SymbolTable.tREAL) {
					template = stg.getInstanceOf("bool_to_real");
				}
			} else if (castFrom.getTypeIndex() == SymbolTable.tCHARACTER) {
				if (castTo.getTypeIndex() == SymbolTable.tBOOLEAN) {
					template = stg.getInstanceOf("char_to_bool");
				} else if (castTo.getTypeIndex() == SymbolTable.tCHARACTER) {
					template = stg.getInstanceOf("char_to_char");
				} else if (castTo.getTypeIndex() == SymbolTable.tINTEGER) {
					template = stg.getInstanceOf("char_to_int");
				} else if (castTo.getTypeIndex() == SymbolTable.tREAL) {
					template = stg.getInstanceOf("char_to_real");
				}
			} else if (castFrom.getTypeIndex() == SymbolTable.tINTEGER) {
				if (castTo.getTypeIndex() == SymbolTable.tBOOLEAN) {
					template = stg.getInstanceOf("int_to_bool");
				} else if (castTo.getTypeIndex() == SymbolTable.tCHARACTER) {
					template = stg.getInstanceOf("int_to_char");
				} else if (castTo.getTypeIndex() == SymbolTable.tINTEGER) {
					template = stg.getInstanceOf("int_to_int");
				} else if (castTo.getTypeIndex() == SymbolTable.tREAL) {
					template = stg.getInstanceOf("int_to_real");
				}
			} else if (castFrom.getTypeIndex() == SymbolTable.tREAL) {
				if (castTo.getTypeIndex() == SymbolTable.tINTEGER) {
					template = stg.getInstanceOf("real_to_int");
				} else if (castTo.getTypeIndex() == SymbolTable.tREAL) {
					template = stg.getInstanceOf("real_to_real");
				}
			}
			
			template.setAttribute("expr_id", expr_id);
			template.setAttribute("expr", expr);
			template.setAttribute("id", id);
			
			return template;
		}

		case DashLexer.ID:
		{
			return getSymbol(t);
		}
		
		case DashLexer.CHARACTER:
		{
			int id = ((DashAST)t).llvmResultID;
			String character = t.getText();
			character = character.replaceAll("'", "");
			
			/*
			 * Bell 	\a
			 * Backspace 	\b
			 * Line Feed 	\n
			 * Carriage Return 	\r
			 * Tab 	\t
			 * Backslash 	\\
			 * Apostrophe 	\'
			 * Quotation Mark 	\"
			 * Null 	\0
			 */
			char val = 0;
			if(character.equals("\\a")) {
				val = 7;
			} else if(character.equals("\\b")) {
				val = '\b';
			} else if(character.equals("\\n")) {
				val = '\n';
			} else if(character.equals("\\r")) {
				val = '\r';
			} else if(character.equals("\\t")) {
				val = '\t';
			} else if(character.equals("\\\\")) {
				val = '\\';
			} else if(character.equals("\\'")) {
				val = '\'';
			} else if(character.equals("\\\"")) {
				val = '\"';
			} else if(character.equals("\\0")) {
				val = '\0';
			} else {
				val = character.charAt(0);
			}
			
			StringTemplate template = stg.getInstanceOf("char_literal");
			template.setAttribute("val", val);
			template.setAttribute("id", id);
			return template;
		}

		case DashLexer.INTEGER:
		{
			int id = ((DashAST) t).llvmResultID;
			StringTemplate template = null;

			int val = Integer.parseInt(t.getText().replaceAll("_", ""));

			template = stg.getInstanceOf("int_literal");
			template.setAttribute("val", val);

			if (t.promoteToType != null && t.promoteToType.getTypeIndex() == SymbolTable.tREAL) {
				template.setAttribute("id", id + "_temp");
				
				StringTemplate promote = stg.getInstanceOf("int_to_real");
				promote.setAttribute("expr_id", id + "_temp");
				promote.setAttribute("expr", template);
				promote.setAttribute("id", id);
				
				return promote;
			} else {
				template.setAttribute("id", id);
			}
			
			return template;
		}
		
		case DashLexer.REAL:
		{
			int id = ((DashAST)t).llvmResultID;
			float val = Float.parseFloat(t.getText().replaceAll("_", ""));
			String hex_val = Long.toHexString(Double.doubleToLongBits(val));
			hex_val = "0x" + hex_val.toUpperCase();
			
			StringTemplate template = stg.getInstanceOf("real_literal");
			template.setAttribute("val", hex_val);
			template.setAttribute("id", id);
			return template;
		}
		
		case DashLexer.True:
		{
			int id = ((DashAST)t).llvmResultID;
			
			StringTemplate template = stg.getInstanceOf("bool_literal");
			template.setAttribute("val", 1);
			template.setAttribute("id", id);
			return template;
		}
		
		case DashLexer.False:
		{
			int id = ((DashAST)t).llvmResultID;
			
			StringTemplate template = stg.getInstanceOf("bool_literal");
			template.setAttribute("val", 0);
			template.setAttribute("id", id);
			return template;
		}

		default:
			/*
			 * Should never get here.
			 */
			throw new RuntimeException("Unrecognized token: " + t.getText());
		}

	}
	
	private StringTemplate assignTuple(int id, VariableSymbol lhsTupleSymbol, int rhsExprId, StringTemplate rhsExpr) {
		VariableSymbol tuple = (VariableSymbol) lhsTupleSymbol;
		TupleTypeSymbol tuple_type = (TupleTypeSymbol) lhsTupleSymbol.type;
		Scope scope = tuple.scope;

		StringTemplate template = null;
		template = stg.getInstanceOf("tuple_assign");
		List<StringTemplate> element_assigns = new ArrayList<StringTemplate>();

		StringTemplate getLocalTuple = null;
		if (scope.getScopeIndex() == SymbolTable.scGLOBAL) {
			getLocalTuple = stg.getInstanceOf("tuple_get_global");
		} else {
			getLocalTuple = stg.getInstanceOf("tuple_get_local");
		}

		getLocalTuple.setAttribute("id", DashAST.getUniqueId());
		getLocalTuple.setAttribute("type_id", tuple_type.tupleTypeIndex);
		getLocalTuple.setAttribute("sym_id", tuple.id);

		List<Symbol> fields = tuple_type.fields;
		for (int i = 0; i <	fields.size(); i++) {
			StringTemplate memberAssign = null;
			StringTemplate getMember = null;

			int field_type = fields.get(i).type.getTypeIndex();
			if (field_type == SymbolTable.tINTEGER) {
				getMember = stg.getInstanceOf("int_get_tuple_member");
				memberAssign = stg.getInstanceOf("int_tuple_assign");
			}
			else if (field_type == SymbolTable.tREAL) {
				getMember = stg.getInstanceOf("real_get_tuple_member");
				memberAssign = stg.getInstanceOf("real_tuple_assign");
			}
			else if (field_type == SymbolTable.tCHARACTER) {
				getMember = stg.getInstanceOf("char_get_tuple_member");
				memberAssign = stg.getInstanceOf("char_tuple_assign");
			}
			else if (field_type == SymbolTable.tBOOLEAN) {
				getMember = stg.getInstanceOf("bool_get_tuple_member");
				memberAssign = stg.getInstanceOf("bool_tuple_assign");
			}

			getMember.setAttribute("id", DashAST.getUniqueId());
			getMember.setAttribute("tuple_expr_id", rhsExprId);
			getMember.setAttribute("tuple_type", tuple_type.tupleTypeIndex);
			getMember.setAttribute("index", i);

			memberAssign.setAttribute("id", DashAST.getUniqueId());
			memberAssign.setAttribute("tuple_expr_id", getLocalTuple.getAttribute("id"));
			memberAssign.setAttribute("tuple_type", tuple_type.tupleTypeIndex);
			memberAssign.setAttribute("index", i);
			memberAssign.setAttribute("expr", getMember);
			memberAssign.setAttribute("expr_id", getMember.getAttribute("id"));

			element_assigns.add(memberAssign);
		}

		template.setAttribute("id", id);
		template.setAttribute("lhs_expr", getLocalTuple);
		template.setAttribute("rhs_expr", rhsExpr);
		template.setAttribute("element_assigns", element_assigns);
		return template;
	}
	
	private StringTemplate assignInterval(int id, boolean declaration, VariableSymbol varSymbol, int rhsExprId, StringTemplate rhsExpr) {
		Scope scope = varSymbol.scope;

		StringTemplate template = null;
		if (declaration) {
			template = stg.getInstanceOf("interval_assign_decl");
		} else {
			template = stg.getInstanceOf("interval_assign");
		}

		StringTemplate getInterval = null;
		if (scope.getScopeIndex() == SymbolTable.scGLOBAL) {
			getInterval = stg.getInstanceOf("vector_get_global_var");
		} else {
			getInterval = stg.getInstanceOf("vector_get_local_var");
		}

		getInterval.setAttribute("id", DashAST.getUniqueId());
		getInterval.setAttribute("sym_id", varSymbol.id);
		
		template.setAttribute("id", id);
		template.setAttribute("interval_var_expr", getInterval);
		template.setAttribute("interval_var_expr_id", getInterval.getAttribute("id"));
		template.setAttribute("rhs_expr",  rhsExpr);
		template.setAttribute("rhs_expr_id", rhsExprId);

		return template;
	}

	private StringTemplate assignVector(DashAST t) {
		DashAST lhs = null;
		DashAST rhs = null;
		
		StringTemplate template = null;
		VariableSymbol varSymbol = null;
		if (t.getToken().getType() == DashLexer.ASSIGN) {
			lhs = (DashAST)t.getChild(0);
			rhs = (DashAST)t.getChild(1);
			varSymbol = (VariableSymbol) ((DashAST)lhs.getChild(0)).symbol;
		} else if(t.getToken().getType() == DashLexer.VAR_DECL) {
			lhs = (DashAST)t.getChild(1);
			rhs = (DashAST)t.getChild(2);
			varSymbol = (VariableSymbol) lhs.symbol;
		}
		
		VectorType vecType = (VectorType) varSymbol.type;
		Scope scope = varSymbol.scope;
		Type elementType = vecType.elementType;
		int elementTypeIndex = vecType.elementType.getTypeIndex();
		DashAST vectorTypeExpr = null;
		boolean infer = false;
		if (vecType.def != null)
			vectorTypeExpr = (DashAST)vecType.def.getChild(1);
		else 
			infer = true;
		
		boolean scalar = false;
		boolean declare = false;
		if (t.getToken().getType() == DashLexer.ASSIGN) {
			if (rhs.evalType.getTypeIndex() == SymbolTable.tINTERVAL ||
					rhs.evalType.getTypeIndex() == SymbolTable.tVECTOR) {
				template = stg.getInstanceOf("vector_assign");
			} else {
				template = stg.getInstanceOf("vector_assign_scalar");
				scalar = true;
			}
		} else if(t.getToken().getType() == DashLexer.VAR_DECL) {
			declare = true;
			
			varSymbol = (VariableSymbol) lhs.symbol;
			if (infer) {
				template = stg.getInstanceOf("vector_assign_decl_infer");
			} else {
				if (rhs.evalType.getTypeIndex() == SymbolTable.tINTERVAL ||
						rhs.evalType.getTypeIndex() == SymbolTable.tVECTOR) {
					if (vectorTypeExpr.getToken().getType() == DashLexer.INFERRED) {
						template = stg.getInstanceOf("vector_assign_decl_infer");
					} else  {
						template = stg.getInstanceOf("vector_assign_decl");
					}
				} else {
					template = stg.getInstanceOf("vector_assign_decl_scalar");
					scalar = true;
				}
			}
		}		
		
		if (!infer) {
			if (vectorTypeExpr.getToken().getType() != DashLexer.INFERRED && declare) {
				StringTemplate vector_size = exec((DashAST) vectorTypeExpr);
	
				if (vector_sizes.add(vector_size.getAttribute("id").toString()))
					template.setAttribute("vector_size", vector_size);
				template.setAttribute("vector_size_id", vector_size.getAttribute("id"));
			}
		}

		StringTemplate getVector = null;
		if (scope.getScopeIndex() == SymbolTable.scGLOBAL) {
			getVector = stg.getInstanceOf("vector_get_global_var");
		} else {
			getVector = stg.getInstanceOf("vector_get_local_var");
		}

		getVector.setAttribute("id", DashAST.getUniqueId());
		getVector.setAttribute("sym_id", varSymbol.id);

		StringTemplate rhsExpr = exec(rhs);
		
		int rhsTypeIndex = 0;
		if (rhs.evalType.getTypeIndex() == SymbolTable.tINTERVAL) {
			//interval_to_vector(id, interval_var_expr, interval_var_expr_id)
			StringTemplate interval = stg.getInstanceOf("interval_to_vector");
			interval.setAttribute("id", DashAST.getUniqueId());
			interval.setAttribute("interval_var_expr", rhsExpr);
			interval.setAttribute("interval_var_expr_id", rhsExpr.getAttribute("id"));
			
			rhsExpr = interval;
			
			rhsTypeIndex = SymbolTable.tINTEGER;
		} else if (rhs.evalType.getTypeIndex() == SymbolTable.tVECTOR) {
			VectorType rhsVType = (VectorType) rhs.evalType;
			rhsTypeIndex = rhsVType.elementType.getTypeIndex();
		}
		
		if (elementTypeIndex == SymbolTable.tREAL &&
				rhsTypeIndex == SymbolTable.tINTEGER) {
			StringTemplate promote = stg.getInstanceOf("vector_to_real");
			promote.setAttribute("id", DashAST.getUniqueId());
			promote.setAttribute("type_name", typeIndexToName.get(rhsTypeIndex));
			promote.setAttribute("vector_var_expr", rhsExpr);
			promote.setAttribute("vector_var_expr_id", rhsExpr.getAttribute("id"));
			
			rhsExpr = promote;
		}

		template.setAttribute("id", t.llvmResultID);
		template.setAttribute("type_name", typeIndexToName.get(elementTypeIndex));
		template.setAttribute("vector_var_expr", getVector);
		template.setAttribute("vector_var_expr_id", getVector.getAttribute("id"));
		template.setAttribute("rhs_expr", rhsExpr);
		template.setAttribute("rhs_expr_id", rhsExpr.getAttribute("id"));
		
		if (scalar)
			template.setAttribute("type", getType(elementType));

		return template;
	}
	
	private StringTemplate assignMatrix(DashAST t) {
		DashAST lhs = null;
		DashAST rhs = null;
		
		StringTemplate template = null;
		VariableSymbol varSymbol = null;
		if (t.getToken().getType() == DashLexer.ASSIGN) {
			lhs = (DashAST)t.getChild(0);
			rhs = (DashAST)t.getChild(1);
			varSymbol = (VariableSymbol) ((DashAST)lhs.getChild(0)).symbol;
		} else if(t.getToken().getType() == DashLexer.VAR_DECL) {
			lhs = (DashAST)t.getChild(1);
			rhs = (DashAST)t.getChild(2);
			varSymbol = (VariableSymbol) lhs.symbol;
		}
		
		MatrixType matType = (MatrixType) varSymbol.type;
		Scope scope = varSymbol.scope;
		Type elementType = matType.elementType;
		int elementTypeIndex = matType.elementType.getTypeIndex();
		
		DashAST matrixRowTypeExpr = null;
		DashAST matrixColumnTypeExpr = null;
		boolean infer = false;
		if (matType.def != null) {
			matrixRowTypeExpr = (DashAST)matType.def.getChild(1);
			matrixColumnTypeExpr = (DashAST)matType.def.getChild(2);
		} else {
			infer = true;
		}
		
		boolean scalar = false;
		boolean declare = false;
		if (t.getToken().getType() == DashLexer.ASSIGN) {
			if (rhs.evalType.getTypeIndex() == SymbolTable.tMATRIX) {
				template = stg.getInstanceOf("matrix_assign");
			} else {
				template = stg.getInstanceOf("matrix_assign_scalar");
				scalar = true;
			}
		} else if(t.getToken().getType() == DashLexer.VAR_DECL) {
			declare = true;
			varSymbol = (VariableSymbol) lhs.symbol;
			
			if (infer) {
				template = stg.getInstanceOf("matrix_assign_decl_infer");
			} else {
				if (rhs.evalType.getTypeIndex() == SymbolTable.tMATRIX) {
					if (matrixRowTypeExpr.getToken().getType() == DashLexer.INFERRED &&
							matrixColumnTypeExpr.getToken().getType() == DashLexer.INFERRED) {
						template = stg.getInstanceOf("matrix_assign_decl_infer");
					} else if (matrixRowTypeExpr.getToken().getType() == DashLexer.INFERRED) {
						template = stg.getInstanceOf("matrix_assign_decl_row_infer");
					} else if (matrixColumnTypeExpr.getToken().getType() == DashLexer.INFERRED) {
						template = stg.getInstanceOf("matrix_assign_decl_column_infer");
					} else {
						template = stg.getInstanceOf("matrix_assign_decl");
					}
					
				} else {
					template = stg.getInstanceOf("matrix_assign_decl_scalar");
					scalar = true;
				}
			}
		}		
		
		if (!infer) {
			if (matrixRowTypeExpr.getToken().getType() != DashLexer.INFERRED && declare) {
				StringTemplate row_size = exec((DashAST) matrixRowTypeExpr);
	
				if (vector_sizes.add(row_size.getAttribute("id").toString()))
					template.setAttribute("row_size", row_size);
				template.setAttribute("row_size_id", row_size.getAttribute("id"));
			}
			
			if (matrixColumnTypeExpr.getToken().getType() != DashLexer.INFERRED && declare) {
				StringTemplate column_size = exec((DashAST) matrixColumnTypeExpr);
	
				if (vector_sizes.add(column_size.getAttribute("id").toString()))
					template.setAttribute("column_size", column_size);
				template.setAttribute("column_size_id", column_size.getAttribute("id"));
			}
		}

		StringTemplate getMatrix = null;
		if (scope.getScopeIndex() == SymbolTable.scGLOBAL) {
			getMatrix = stg.getInstanceOf("matrix_get_global_var");
		} else {
			getMatrix = stg.getInstanceOf("matrix_get_local_var");
		}

		getMatrix.setAttribute("id", DashAST.getUniqueId());
		getMatrix.setAttribute("sym_id", varSymbol.id);

		StringTemplate rhsExpr = exec(rhs);
		
		int rhsTypeIndex = 0;
		if (rhs.evalType.getTypeIndex() == SymbolTable.tMATRIX) {
			MatrixType rhsMatType = (MatrixType) rhs.evalType;
			rhsTypeIndex = rhsMatType.elementType.getTypeIndex();
		}
		
		if (elementTypeIndex == SymbolTable.tREAL &&
				rhsTypeIndex == SymbolTable.tINTEGER) {
			StringTemplate promote = stg.getInstanceOf("matrix_to_real");
			promote.setAttribute("id", DashAST.getUniqueId());
			promote.setAttribute("type_name", typeIndexToName.get(rhsTypeIndex));
			promote.setAttribute("matrix_var_expr", rhsExpr);
			promote.setAttribute("matrix_var_expr_id", rhsExpr.getAttribute("id"));
			
			rhsExpr = promote;
		}
		
		if (elementTypeIndex == SymbolTable.tREAL &&
				rhs.evalType.getTypeIndex() == SymbolTable.tINTEGER) {
			//int_to_real(id, expr, expr_id)
			StringTemplate promote = stg.getInstanceOf("int_to_real");
			promote.setAttribute("id", DashAST.getUniqueId());
			promote.setAttribute("expr", rhsExpr);
			promote.setAttribute("expr_id", rhsExpr.getAttribute("id"));
			
			rhsExpr = promote;
		}

		template.setAttribute("id", t.llvmResultID);
		template.setAttribute("type_name", typeIndexToName.get(elementTypeIndex));
		template.setAttribute("matrix_var_expr", getMatrix);
		template.setAttribute("matrix_var_expr_id", getMatrix.getAttribute("id"));
		template.setAttribute("rhs_expr", rhsExpr);
		template.setAttribute("rhs_expr_id", rhsExpr.getAttribute("id"));
		
		if (scalar)
			template.setAttribute("llvm_type", getType(elementType));

		return template;
	}

	private StringTemplate comparisonPrimaryOrTuple(DashAST t, LLVMOps op) {
		Type type = ((DashAST)t.getChild(0)).evalType;

		if (type.getTypeIndex() == SymbolTable.tTUPLE) {
			return tupleOperation(t, op);
		}

		return operation(t, op);
	}

	private StringTemplate tupleOperation(DashAST t, LLVMOps op) {
		String id = Integer.toString(((DashAST)t).llvmResultID);
		Type type = ((DashAST)t.getChild(0)).evalType;
		
		StringTemplate lhs = exec((DashAST)t.getChild(0));
		String lhs_id = Integer.toString(((DashAST)t.getChild(0)).llvmResultID);
		
		StringTemplate rhs = exec((DashAST)t.getChild(1));
		String rhs_id = Integer.toString(((DashAST)t.getChild(1)).llvmResultID);
		
		String code = "";
		
		StringTemplate template_init = null;
		template_init = stg.getInstanceOf("tuple_cmp_init");
		template_init.setAttribute("lhs_expr", lhs);
		template_init.setAttribute("rhs_expr", rhs);
		template_init.setAttribute("id", id);
		
		code += template_init.toString() + "\n";
		
		TupleTypeSymbol tuple = (TupleTypeSymbol) type;
		
		for (int i = 0; i < tuple.fields.size(); i++) {
			VariableSymbol s = (VariableSymbol) tuple.fields.get(i);
			int type_index = s.type.getTypeIndex();
			
			StringTemplate template_cmp = null;
			if (type_index == SymbolTable.tBOOLEAN)
				template_cmp = stg.getInstanceOf("bool_tuple_eq_member");
			else if (type_index == SymbolTable.tCHARACTER)
				template_cmp = stg.getInstanceOf("char_tuple_eq_member");
			else if (type_index == SymbolTable.tINTEGER)
				template_cmp = stg.getInstanceOf("int_tuple_eq_member");
			else if (type_index == SymbolTable.tREAL)
				template_cmp = stg.getInstanceOf("real_tuple_eq_member");
			
			template_cmp.setAttribute("lhs_expr_id", lhs_id);
			template_cmp.setAttribute("rhs_expr_id", rhs_id);
			template_cmp.setAttribute("index", i);
			template_cmp.setAttribute("tuple_type", tuple.tupleTypeIndex);
			template_cmp.setAttribute("id", id);
			
			code += template_cmp.toString() + "\n";
		}
		
		StringTemplate template_result = null;
		if (op == LLVMOps.EQ) {
			template_result = stg.getInstanceOf("tuple_cmp_eq_result");
		} else if (op == LLVMOps.NE) {
			template_result = stg.getInstanceOf("tuple_cmp_ne_result");
		}
		
		template_result.setAttribute("id", id);
		
		code += template_result.toString() + "\n";
		
		return new StringTemplate(code);
	}

	private StringTemplate getSymbol(DashAST t) {
		int id = ((DashAST)t).llvmResultID;
		
		Symbol sym = ((DashAST)t).symbol;
		int sym_id = sym.id;
		Scope scope = sym.scope;
		int type = sym.type.getTypeIndex();
		
		StringTemplate template = null;
		if (scope.getScopeIndex() == SymbolTable.scGLOBAL) {
			if (type == SymbolTable.tINTEGER) {
				template = stg.getInstanceOf("int_get_global");
			} else if (type == SymbolTable.tREAL) {
				template = stg.getInstanceOf("real_get_global");
			} else if (type == SymbolTable.tCHARACTER) {
				template = stg.getInstanceOf("char_get_global");
			} else if (type == SymbolTable.tBOOLEAN) {
				template = stg.getInstanceOf("bool_get_global");
			} else if (type == SymbolTable.tTUPLE) {
				template = stg.getInstanceOf("tuple_get_global");
				template.setAttribute("type_id", ((TupleTypeSymbol)sym.type).tupleTypeIndex);
			} else if (type == SymbolTable.tINTERVAL) {
				template = stg.getInstanceOf("interval_get_global");
			} else if (type == SymbolTable.tVECTOR) {
				template = stg.getInstanceOf("vector_get_global");
			} else if (type == SymbolTable.tMATRIX) {
				template = stg.getInstanceOf("matrix_get_global");
			}
		} else {
			if (type == SymbolTable.tINTEGER) {
				template = stg.getInstanceOf("int_get_local");
			} else if (type == SymbolTable.tREAL) {
				template = stg.getInstanceOf("real_get_local");
			} else if (type == SymbolTable.tCHARACTER) {
				template = stg.getInstanceOf("char_get_local");
			} else if (type == SymbolTable.tBOOLEAN) {
				template = stg.getInstanceOf("bool_get_local");
			} else if (type == SymbolTable.tTUPLE) {
				template = stg.getInstanceOf("tuple_get_local");
				template.setAttribute("type_id", ((TupleTypeSymbol)sym.type).tupleTypeIndex);
			} else if (type == SymbolTable.tINTERVAL) {
				template = stg.getInstanceOf("interval_get_local");
			} else if (type == SymbolTable.tVECTOR) {
				template = stg.getInstanceOf("vector_get_local");
			} else if (type == SymbolTable.tMATRIX) {
				template = stg.getInstanceOf("matrix_get_local");
			}
		}
		template.setAttribute("sym_id", sym_id);
		
		if (t.promoteToType != null && t.promoteToType.getTypeIndex() == SymbolTable.tREAL) {
			template.setAttribute("id", id + "_temp");
			
			StringTemplate promote = stg.getInstanceOf("int_to_real");
			promote.setAttribute("expr_id", id + "_temp");
			promote.setAttribute("expr", template);
			promote.setAttribute("id", id);
			
			return promote;
		} else {
			template.setAttribute("id", id);
		}
		return template;
	}
	
	private StringTemplate getType(Type type) {
		StringTemplate type_template = null;
		if (type.getTypeIndex() == SymbolTable.tINTEGER) {
			type_template = stg.getInstanceOf("int_type");
		} else if (type.getTypeIndex() == SymbolTable.tREAL) {
			type_template = stg.getInstanceOf("real_type");
		} else if (type.getTypeIndex() == SymbolTable.tCHARACTER) {
			type_template = stg.getInstanceOf("char_type");
		} else if (type.getTypeIndex() == SymbolTable.tBOOLEAN) {
			type_template = stg.getInstanceOf("bool_type");
		} else if (type.getTypeIndex() == SymbolTable.tTUPLE) {
			type_template = stg.getInstanceOf("tuple_type");
			type_template.setAttribute("type_id", ((TupleTypeSymbol)type).tupleTypeIndex);
		} else if (type.getTypeIndex() == SymbolTable.tINTERVAL) {
			type_template = stg.getInstanceOf("interval_type");
		} else if (type.getTypeIndex() == SymbolTable.tVECTOR) {
			type_template = stg.getInstanceOf("vector_type");
		} else if (type.getTypeIndex() == SymbolTable.tMATRIX) {
			type_template = stg.getInstanceOf("matrix_type");
		} else if (type.getTypeIndex() == SymbolTable.tVOID) {
			type_template = stg.getInstanceOf("void_type");
		}
		
		return type_template;
	}
	
	private boolean isNumber(int type) {
		return (type == SymbolTable.tINTEGER ||
					type == SymbolTable.tREAL);
	}
	
	private boolean isScalar(int type) {
		return (type == SymbolTable.tBOOLEAN ||
					type == SymbolTable.tCHARACTER ||
						type == SymbolTable.tINTEGER ||
							type == SymbolTable.tREAL);
	}

	private StringTemplate operation(DashAST t, LLVMOps op)
	{
		String id = Integer.toString(((DashAST)t).llvmResultID);
		int type = t.evalType.getTypeIndex();
		
		
		StringTemplate lhs = exec((DashAST)t.getChild(0));
		String lhs_id = Integer.toString(((DashAST)t.getChild(0)).llvmResultID);
		int lhs_type = ((DashAST)t.getChild(0)).evalType.getTypeIndex();

		if (((DashAST)t.getChild(0)).promoteToType != null && 
				((DashAST)t.getChild(0)).promoteToType.getTypeIndex() == SymbolTable.tREAL) {
			lhs_type = SymbolTable.tREAL;
		}
		
		StringTemplate rhs = exec((DashAST)t.getChild(1));
		String rhs_id = Integer.toString(((DashAST)t.getChild(1)).llvmResultID);
		int rhs_type = ((DashAST)t.getChild(1)).evalType.getTypeIndex();
		
		if (((DashAST)t.getChild(1)).promoteToType != null && 
				((DashAST)t.getChild(1)).promoteToType.getTypeIndex() == SymbolTable.tREAL) {
			rhs_type = SymbolTable.tREAL;
		}
		
		// Find vector type for operations
		int elementTypeIndex = -1;
		int lhs_elementTypeIndex = -1;
		int rhs_elementTypeIndex = -1;
		if (type == SymbolTable.tVECTOR) {
			elementTypeIndex = ((VectorType) t.evalType).elementType.getTypeIndex();
		} else if (type == SymbolTable.tMATRIX) {
			elementTypeIndex = ((MatrixType) t.evalType).elementType.getTypeIndex();
		}
		
		if (lhs_type == SymbolTable.tVECTOR) {
			VectorType vector_type = (VectorType)((DashAST)t.getChild(0)).evalType;
			lhs_elementTypeIndex = vector_type.elementType.getTypeIndex();
			
			if (lhs_elementTypeIndex > elementTypeIndex) {
				elementTypeIndex = lhs_elementTypeIndex;
			}
		} else if (lhs_type == SymbolTable.tMATRIX) {
			MatrixType mat_type = (MatrixType)((DashAST)t.getChild(0)).evalType;
			lhs_elementTypeIndex = mat_type.elementType.getTypeIndex();
			
			if (lhs_elementTypeIndex > elementTypeIndex) {
				elementTypeIndex = lhs_elementTypeIndex;
			}
		}
		
		if (rhs_type == SymbolTable.tVECTOR) {
			VectorType vector_type = (VectorType)((DashAST)t.getChild(1)).evalType;
			rhs_elementTypeIndex = vector_type.elementType.getTypeIndex();
			
			if (rhs_elementTypeIndex > elementTypeIndex) {
				elementTypeIndex = rhs_elementTypeIndex;
			}
		} else if (rhs_type == SymbolTable.tMATRIX) {
			MatrixType mat_type = (MatrixType)((DashAST)t.getChild(1)).evalType;
			rhs_elementTypeIndex = mat_type.elementType.getTypeIndex();
			
			if (rhs_elementTypeIndex > elementTypeIndex) {
				elementTypeIndex = rhs_elementTypeIndex;
			}
		}
		
		// Promotion
		if (lhs_type == SymbolTable.tINTERVAL && rhs_type == SymbolTable.tVECTOR) {
			//interval_to_vector(id, interval_var_expr, interval_var_expr_id)
			StringTemplate interval = stg.getInstanceOf("interval_to_vector");
			interval.setAttribute("id", DashAST.getUniqueId());
			interval.setAttribute("interval_var_expr", lhs);
			interval.setAttribute("interval_var_expr_id", lhs_id);
			
			lhs = interval;
			lhs_id = interval.getAttribute("id").toString();
			lhs_type = SymbolTable.tVECTOR;
			
			lhs_elementTypeIndex = SymbolTable.tINTEGER;

		}
		
		if (lhs_type == SymbolTable.tVECTOR && rhs_type == SymbolTable.tINTERVAL) {
			//interval_to_vector(id, interval_var_expr, interval_var_expr_id)
			StringTemplate interval = stg.getInstanceOf("interval_to_vector");
			interval.setAttribute("id", DashAST.getUniqueId());
			interval.setAttribute("interval_var_expr", rhs);
			interval.setAttribute("interval_var_expr_id", rhs_id);
			
			rhs = interval;
			rhs_id = interval.getAttribute("id").toString();
			rhs_type = SymbolTable.tVECTOR;
			
			rhs_elementTypeIndex = SymbolTable.tINTEGER;
		}
		
		if ((rhs_elementTypeIndex == SymbolTable.tREAL || 
				rhs_type == SymbolTable.tREAL) &&
				(lhs_elementTypeIndex == SymbolTable.tINTEGER &&
				lhs_type == SymbolTable.tVECTOR)) {
			StringTemplate promote = stg.getInstanceOf("vector_to_real");
			promote.setAttribute("id", DashAST.getUniqueId());
			promote.setAttribute("type_name", typeIndexToName.get(lhs_elementTypeIndex));
			promote.setAttribute("vector_var_expr", lhs);
			promote.setAttribute("vector_var_expr_id", lhs.getAttribute("id"));
			
			lhs = promote;
			lhs_id = promote.getAttribute("id").toString();
		}
		
		if ((lhs_elementTypeIndex == SymbolTable.tREAL || 
				lhs_type == SymbolTable.tREAL) &&
				(rhs_elementTypeIndex == SymbolTable.tINTEGER &&
				rhs_type == SymbolTable.tVECTOR)) {
			StringTemplate promote = stg.getInstanceOf("vector_to_real");
			promote.setAttribute("id", DashAST.getUniqueId());
			promote.setAttribute("type_name", typeIndexToName.get(rhs_elementTypeIndex));
			promote.setAttribute("vector_var_expr", rhs);
			promote.setAttribute("vector_var_expr_id", rhs.getAttribute("id"));
			
			rhs = promote;
			rhs_id = promote.getAttribute("id").toString();
		}
		
		if ((rhs_elementTypeIndex == SymbolTable.tREAL || 
				rhs_type == SymbolTable.tREAL) &&
				(lhs_elementTypeIndex == SymbolTable.tINTEGER &&
				lhs_type == SymbolTable.tMATRIX)) {
			StringTemplate promote = stg.getInstanceOf("matrix_to_real");
			promote.setAttribute("id", DashAST.getUniqueId());
			promote.setAttribute("type_name", typeIndexToName.get(lhs_elementTypeIndex));
			promote.setAttribute("matrix_var_expr", lhs);
			promote.setAttribute("matrix_var_expr_id", lhs.getAttribute("id"));
			
			lhs = promote;
			lhs_id = promote.getAttribute("id").toString();
		}
		
		if ((lhs_elementTypeIndex == SymbolTable.tREAL || 
				lhs_type == SymbolTable.tREAL) &&
				(rhs_elementTypeIndex == SymbolTable.tINTEGER &&
				rhs_type == SymbolTable.tMATRIX)) {
			StringTemplate promote = stg.getInstanceOf("matrix_to_real");
			promote.setAttribute("id", DashAST.getUniqueId());
			promote.setAttribute("type_name", typeIndexToName.get(rhs_elementTypeIndex));
			promote.setAttribute("matrix_var_expr", rhs);
			promote.setAttribute("matrix_var_expr_id", rhs.getAttribute("id"));
			
			rhs = promote;
			rhs_id = promote.getAttribute("id").toString();
		}
		
		if (t.promoteToType != null && 
				t.promoteToType.getTypeIndex() == SymbolTable.tREAL) {
			
			type = SymbolTable.tREAL;
			
			if (((DashAST)t.getChild(0)).evalType.getTypeIndex() == SymbolTable.tINTEGER) {
				StringTemplate promote = stg.getInstanceOf("int_to_real");
				promote.setAttribute("expr_id", lhs_id);
				promote.setAttribute("expr", lhs);
				promote.setAttribute("id", lhs_id + "_lhs_tmp");
				
				lhs = promote;
				lhs_id = lhs_id + "_lhs_tmp";
				lhs_type = type;
			}
			
			if (((DashAST)t.getChild(1)).evalType.getTypeIndex() == SymbolTable.tINTEGER) {
				StringTemplate promote = stg.getInstanceOf("int_to_real");
				promote.setAttribute("expr_id", rhs_id);
				promote.setAttribute("expr", rhs);
				promote.setAttribute("id", rhs_id + "_rhs_tmp");
				
				rhs = promote;
				rhs_id = rhs_id + "_rhs_tmp";
				rhs_type = type;
			}
		}

		boolean insertVectorSizeCheck = false;
		boolean insertMatrixSizeCheck = false;

		//TODO: Update LT LE GT GE EQ NE to handle vectors and scalars other than integers
		StringTemplate template = null;
		switch (op) {
		case AND: {
			if (type == SymbolTable.tVECTOR) {
				if (lhs_type == SymbolTable.tVECTOR &&
						rhs_type == SymbolTable.tVECTOR) {
					template = stg.getInstanceOf("vector_and_vector");
					insertVectorSizeCheck = true;
				} else if (lhs_type == SymbolTable.tVECTOR &&
						rhs_type == SymbolTable.tBOOLEAN) {
					template = stg.getInstanceOf("vector_and_scalar");
				} else if (rhs_type == SymbolTable.tVECTOR &&
						lhs_type == SymbolTable.tBOOLEAN) {
					template = stg.getInstanceOf("scalar_and_vector");
				}
			} else if (type == SymbolTable.tMATRIX) {
				if (lhs_type == SymbolTable.tMATRIX &&
						rhs_type == SymbolTable.tMATRIX) {
					template = stg.getInstanceOf("matrix_and_matrix");
					insertMatrixSizeCheck = true;
				} else if (lhs_type == SymbolTable.tMATRIX &&
						rhs_type == SymbolTable.tBOOLEAN) {
					template = stg.getInstanceOf("matrix_and_scalar");
				} else if (rhs_type == SymbolTable.tMATRIX &&
						lhs_type == SymbolTable.tBOOLEAN) {
					template = stg.getInstanceOf("scalar_and_matrix");
				}
			} else {
				if (lhs_type == SymbolTable.tBOOLEAN) {
					template = stg.getInstanceOf("bool_and");
				}
			}

			break;
		}
		case OR: {
			if (type == SymbolTable.tVECTOR) {
				if (lhs_type == SymbolTable.tVECTOR &&
						rhs_type == SymbolTable.tVECTOR) {
					template = stg.getInstanceOf("vector_or_vector");
					insertVectorSizeCheck = true;
				} else if (lhs_type == SymbolTable.tVECTOR &&
						rhs_type == SymbolTable.tBOOLEAN) {
					template = stg.getInstanceOf("vector_or_scalar");
				} else if (rhs_type == SymbolTable.tVECTOR &&
						lhs_type == SymbolTable.tBOOLEAN) {
					template = stg.getInstanceOf("scalar_or_vector");
				}
			} else if (type == SymbolTable.tMATRIX) {
				if (lhs_type == SymbolTable.tMATRIX &&
						rhs_type == SymbolTable.tMATRIX) {
					template = stg.getInstanceOf("matrix_or_matrix");
					insertMatrixSizeCheck = true;
				} else if (lhs_type == SymbolTable.tMATRIX &&
						rhs_type == SymbolTable.tBOOLEAN) {
					template = stg.getInstanceOf("matrix_or_scalar");
				} else if (rhs_type == SymbolTable.tMATRIX &&
						lhs_type == SymbolTable.tBOOLEAN) {
					template = stg.getInstanceOf("scalar_or_matrix");
				}
			} else {
				if (lhs_type == SymbolTable.tBOOLEAN) {
					template = stg.getInstanceOf("bool_or");
				}
			}
			break;
		}
		case XOR: {
			if (type == SymbolTable.tVECTOR) {
				if (lhs_type == SymbolTable.tVECTOR &&
						rhs_type == SymbolTable.tVECTOR) {
					template = stg.getInstanceOf("vector_xor_vector");
					insertVectorSizeCheck = true;
				} else if (lhs_type == SymbolTable.tVECTOR &&
						rhs_type == SymbolTable.tBOOLEAN) {
					template = stg.getInstanceOf("vector_xor_scalar");
				} else if (rhs_type == SymbolTable.tVECTOR &&
						lhs_type == SymbolTable.tBOOLEAN) {
					template = stg.getInstanceOf("scalar_xor_vector");
				}
			} else if (type == SymbolTable.tMATRIX) {
				if (lhs_type == SymbolTable.tMATRIX &&
						rhs_type == SymbolTable.tMATRIX) {
					template = stg.getInstanceOf("matrix_xor_matrix");
					insertMatrixSizeCheck = true;
				} else if (lhs_type == SymbolTable.tMATRIX &&
						rhs_type == SymbolTable.tBOOLEAN) {
					template = stg.getInstanceOf("matrix_xor_scalar");
				} else if (rhs_type == SymbolTable.tMATRIX &&
						lhs_type == SymbolTable.tBOOLEAN) {
					template = stg.getInstanceOf("scalar_xor_matrix");
				}
			} else {
				if (lhs_type == SymbolTable.tBOOLEAN) {
					template = stg.getInstanceOf("bool_xor");
				}
			}
			break;
		}
		case EQ:
			if (lhs_type == SymbolTable.tINTERVAL &&
				rhs_type == SymbolTable.tINTERVAL) {
				template = stg.getInstanceOf("interval_eq_interval");
			}  else if (lhs_type == SymbolTable.tVECTOR &&
					rhs_type == SymbolTable.tVECTOR) {
				template = stg.getInstanceOf("vector_eq_vector");
			} else if (lhs_type == SymbolTable.tVECTOR &&
					isScalar(rhs_type)) {
				template = stg.getInstanceOf("vector_eq_scalar");
			} else if (rhs_type == SymbolTable.tVECTOR &&
					isScalar(lhs_type)) {
				template = stg.getInstanceOf("scalar_eq_vector"); // Need to know what side is scalar
			} else if (lhs_type == SymbolTable.tMATRIX &&
					rhs_type == SymbolTable.tMATRIX) {
				template = stg.getInstanceOf("matrix_eq_matrix");
			} else if (lhs_type == SymbolTable.tMATRIX &&
					isScalar(rhs_type)) {
				template = stg.getInstanceOf("matrix_eq_scalar");
			} else if (rhs_type == SymbolTable.tMATRIX &&
					isScalar(lhs_type)) {
				template = stg.getInstanceOf("scalar_eq_matrix"); // Need to know what side is scalar
			} else if (lhs_type == SymbolTable.tINTEGER) {
				template = stg.getInstanceOf("int_eq");
			} else if (lhs_type == SymbolTable.tREAL) {
				template = stg.getInstanceOf("real_eq");
			} else if (lhs_type == SymbolTable.tCHARACTER) {
				template = stg.getInstanceOf("char_eq");
			} else if (lhs_type == SymbolTable.tBOOLEAN) {
				template = stg.getInstanceOf("bool_eq");
			}
			break;
		case NE:
			if (lhs_type == SymbolTable.tINTERVAL &&
				rhs_type == SymbolTable.tINTERVAL) {
				template = stg.getInstanceOf("interval_ne_interval");
			} else if (lhs_type == SymbolTable.tVECTOR &&
					rhs_type == SymbolTable.tVECTOR) {
				template = stg.getInstanceOf("vector_ne_vector");
			} else if (lhs_type == SymbolTable.tVECTOR &&
					isScalar(rhs_type)) {
				template = stg.getInstanceOf("vector_ne_scalar");
			} else if (rhs_type == SymbolTable.tVECTOR &&
					isScalar(lhs_type)) {
				template = stg.getInstanceOf("scalar_ne_vector"); // Need to know what side is scalar
			} else if (lhs_type == SymbolTable.tMATRIX &&
					rhs_type == SymbolTable.tMATRIX) {
				template = stg.getInstanceOf("matrix_ne_matrix");
			} else if (lhs_type == SymbolTable.tMATRIX &&
					isScalar(rhs_type)) {
				template = stg.getInstanceOf("matrix_ne_scalar");
			} else if (rhs_type == SymbolTable.tMATRIX &&
					isScalar(lhs_type)) {
				template = stg.getInstanceOf("scalar_ne_matrix"); // Need to know what side is scalar
			} else if (lhs_type == SymbolTable.tINTEGER) {
				template = stg.getInstanceOf("int_ne");
			} else if (lhs_type == SymbolTable.tREAL) {
				template = stg.getInstanceOf("real_ne");
			} else if (lhs_type == SymbolTable.tCHARACTER) {
				template = stg.getInstanceOf("char_ne");
			} else if (lhs_type == SymbolTable.tBOOLEAN) {
				template = stg.getInstanceOf("bool_ne");
			}
			break;
		case LT:
			if (type == SymbolTable.tINTERVAL) {
				if (lhs_type == SymbolTable.tINTERVAL &&
						rhs_type == SymbolTable.tINTERVAL) {
					template = stg.getInstanceOf("interval_lt_interval");
				}
			} else if (type == SymbolTable.tVECTOR) {
				if (lhs_type == SymbolTable.tVECTOR &&
						rhs_type == SymbolTable.tVECTOR) {
					template = stg.getInstanceOf("vector_lt_vector");
					insertVectorSizeCheck = true;
				} else if (lhs_type == SymbolTable.tVECTOR &&
						isScalar(rhs_type)) {
					template = stg.getInstanceOf("vector_lt_scalar");
				} else if (rhs_type == SymbolTable.tVECTOR &&
						isScalar(lhs_type)) {
					template = stg.getInstanceOf("scalar_lt_vector"); // Need to know what side is scalar
				}
			} else if (type == SymbolTable.tMATRIX) {
				if (lhs_type == SymbolTable.tMATRIX &&
						rhs_type == SymbolTable.tMATRIX) {
					template = stg.getInstanceOf("matrix_lt_matrix");
					insertMatrixSizeCheck = true;
				} else if (lhs_type == SymbolTable.tVECTOR &&
						isScalar(rhs_type)) {
					template = stg.getInstanceOf("matrix_lt_scalar");
				} else if (rhs_type == SymbolTable.tMATRIX &&
						isScalar(lhs_type)) {
					template = stg.getInstanceOf("scalar_lt_matrix"); // Need to know what side is scalar
				}
			} else {
				if (lhs_type == SymbolTable.tINTEGER) {
					template = stg.getInstanceOf("int_lt");
				} else if (lhs_type == SymbolTable.tREAL) {
					template = stg.getInstanceOf("real_lt");
				} else if (lhs_type == SymbolTable.tCHARACTER) {
					template = stg.getInstanceOf("char_lt");
				} else if (lhs_type == SymbolTable.tBOOLEAN) {
					template = stg.getInstanceOf("bool_lt");
				}
			}
			break;
		case LE:
			if (type == SymbolTable.tINTERVAL) {
				if (lhs_type == SymbolTable.tINTERVAL &&
						rhs_type == SymbolTable.tINTERVAL) {
					template = stg.getInstanceOf("interval_le_interval");
				}
			} else if (type == SymbolTable.tVECTOR) {
				if (lhs_type == SymbolTable.tVECTOR &&
						rhs_type == SymbolTable.tVECTOR) {
					template = stg.getInstanceOf("vector_le_vector");
					insertVectorSizeCheck = true;
				} else if (lhs_type == SymbolTable.tVECTOR &&
						isScalar(rhs_type)) {
					template = stg.getInstanceOf("vector_le_scalar");
				} else if (rhs_type == SymbolTable.tVECTOR &&
						isScalar(lhs_type)) {
					template = stg.getInstanceOf("scalar_le_vector"); // Need to know what side is scalar
				}
			} else if (type == SymbolTable.tMATRIX) {
				if (lhs_type == SymbolTable.tMATRIX &&
						rhs_type == SymbolTable.tMATRIX) {
					template = stg.getInstanceOf("matrix_le_matrix");
					insertMatrixSizeCheck = true;
				} else if (lhs_type == SymbolTable.tVECTOR &&
						isScalar(rhs_type)) {
					template = stg.getInstanceOf("matrix_le_scalar");
				} else if (rhs_type == SymbolTable.tMATRIX &&
						isScalar(lhs_type)) {
					template = stg.getInstanceOf("scalar_le_matrix"); // Need to know what side is scalar
				}
			} else {
				if (lhs_type == SymbolTable.tINTEGER) {
					template = stg.getInstanceOf("int_le");
				} else if (lhs_type == SymbolTable.tREAL) {
					template = stg.getInstanceOf("real_le");
				} else if (lhs_type == SymbolTable.tCHARACTER) {
					template = stg.getInstanceOf("char_le");
				} else if (lhs_type == SymbolTable.tBOOLEAN) {
					template = stg.getInstanceOf("bool_le");
				}
			}
			break;
		case GT:
			if (type == SymbolTable.tINTERVAL) {
				if (lhs_type == SymbolTable.tINTERVAL &&
						rhs_type == SymbolTable.tINTERVAL) {
					template = stg.getInstanceOf("interval_gt_interval");
				}
			} else if (type == SymbolTable.tVECTOR) {
				if (lhs_type == SymbolTable.tVECTOR &&
						rhs_type == SymbolTable.tVECTOR) {
					template = stg.getInstanceOf("vector_gt_vector");
					insertVectorSizeCheck = true;
				} else if (lhs_type == SymbolTable.tVECTOR &&
						isScalar(rhs_type)) {
					template = stg.getInstanceOf("vector_gt_scalar");
				} else if (rhs_type == SymbolTable.tVECTOR &&
						isScalar(lhs_type)) {
					template = stg.getInstanceOf("scalar_gt_vector"); // Need to know what side is scalar
				}
			} else if (type == SymbolTable.tMATRIX) {
				if (lhs_type == SymbolTable.tMATRIX &&
						rhs_type == SymbolTable.tMATRIX) {
					template = stg.getInstanceOf("matrix_gt_matrix");
					insertMatrixSizeCheck = true;
				} else if (lhs_type == SymbolTable.tVECTOR &&
						isScalar(rhs_type)) {
					template = stg.getInstanceOf("matrix_gt_scalar");
				} else if (rhs_type == SymbolTable.tMATRIX &&
						isScalar(lhs_type)) {
					template = stg.getInstanceOf("scalar_gt_matrix"); // Need to know what side is scalar
				}
			} else {
				if (lhs_type == SymbolTable.tINTEGER) {
					template = stg.getInstanceOf("int_gt");
				} else if (lhs_type == SymbolTable.tREAL) {
					template = stg.getInstanceOf("real_gt");
				} else if (lhs_type == SymbolTable.tCHARACTER) {
					template = stg.getInstanceOf("char_gt");
				} else if (lhs_type == SymbolTable.tBOOLEAN) {
					template = stg.getInstanceOf("bool_gt");
				}
			}
			break;
		case GE:
			if (type == SymbolTable.tINTERVAL) {
				if (lhs_type == SymbolTable.tINTERVAL &&
						rhs_type == SymbolTable.tINTERVAL) {
					template = stg.getInstanceOf("interval_ge_interval");
				}
			} else if (type == SymbolTable.tVECTOR) {
				if (lhs_type == SymbolTable.tVECTOR &&
						rhs_type == SymbolTable.tVECTOR) {
					template = stg.getInstanceOf("vector_ge_vector");
					insertVectorSizeCheck = true;
				} else if (lhs_type == SymbolTable.tVECTOR &&
						isScalar(rhs_type)) {
					template = stg.getInstanceOf("vector_ge_scalar");
				} else if (rhs_type == SymbolTable.tVECTOR &&
						isScalar(lhs_type)) {
					template = stg.getInstanceOf("scalar_ge_vector"); // Need to know what side is scalar
				}
			} else if (type == SymbolTable.tMATRIX) {
				if (lhs_type == SymbolTable.tMATRIX &&
						rhs_type == SymbolTable.tMATRIX) {
					template = stg.getInstanceOf("matrix_ge_matrix");
					insertMatrixSizeCheck = true;
				} else if (lhs_type == SymbolTable.tVECTOR &&
						isScalar(rhs_type)) {
					template = stg.getInstanceOf("matrix_ge_scalar");
				} else if (rhs_type == SymbolTable.tMATRIX &&
						isScalar(lhs_type)) {
					template = stg.getInstanceOf("scalar_ge_matrix"); // Need to know what side is scalar
				}
			} else {
				if (lhs_type == SymbolTable.tINTEGER) {
					template = stg.getInstanceOf("int_ge");
				} else if (lhs_type == SymbolTable.tREAL) {
					template = stg.getInstanceOf("real_ge");
				} else if (lhs_type == SymbolTable.tCHARACTER) {
					template = stg.getInstanceOf("char_ge");
				} else if (lhs_type == SymbolTable.tBOOLEAN) {
					template = stg.getInstanceOf("bool_ge");
				}
			}
			break;
		case ADD:
			if (type == SymbolTable.tINTERVAL) {
				if (lhs_type == SymbolTable.tINTERVAL &&
						rhs_type == SymbolTable.tINTERVAL) {
					template = stg.getInstanceOf("interval_add_interval");
				}
			} else if (type == SymbolTable.tVECTOR) {
				if (lhs_type == SymbolTable.tVECTOR &&
						rhs_type == SymbolTable.tVECTOR) {
					template = stg.getInstanceOf("vector_add_vector");
					insertVectorSizeCheck = true;
				} else if (lhs_type == SymbolTable.tVECTOR &&
						isNumber(rhs_type)) {
					template = stg.getInstanceOf("vector_add_scalar");
				} else if (rhs_type == SymbolTable.tVECTOR &&
						isNumber(lhs_type)) {
					template = stg.getInstanceOf("scalar_add_vector"); // Need to know what side is scalar
				}
			} else if (type == SymbolTable.tMATRIX) {
				if (lhs_type == SymbolTable.tMATRIX &&
						rhs_type == SymbolTable.tMATRIX) {
					template = stg.getInstanceOf("matrix_add_matrix");
					insertMatrixSizeCheck = true;
				} else if (lhs_type == SymbolTable.tMATRIX &&
						isNumber(rhs_type)) {
					template = stg.getInstanceOf("matrix_add_scalar");
				} else if (rhs_type == SymbolTable.tMATRIX &&
						isNumber(lhs_type)) {
					template = stg.getInstanceOf("scalar_add_matrix"); // Need to know what side is scalar
				}
			} else {
				if (lhs_type == SymbolTable.tINTEGER) {
					template = stg.getInstanceOf("int_add");
				} else if (lhs_type == SymbolTable.tREAL) {
					template = stg.getInstanceOf("real_add");
				}
			}
			break;
		case SUB:
			if (type == SymbolTable.tINTERVAL) {
				if (lhs_type == SymbolTable.tINTERVAL &&
						rhs_type == SymbolTable.tINTERVAL) {
					template = stg.getInstanceOf("interval_subtract_interval");
				}
			} else if (type == SymbolTable.tVECTOR) {
				if (lhs_type == SymbolTable.tVECTOR &&
						rhs_type == SymbolTable.tVECTOR) {
					template = stg.getInstanceOf("vector_subtract_vector");
					insertVectorSizeCheck = true;
				} else if (lhs_type == SymbolTable.tVECTOR &&
						isNumber(rhs_type)) {
					template = stg.getInstanceOf("vector_subtract_scalar");
				}  else if (rhs_type == SymbolTable.tVECTOR &&
						isNumber(lhs_type)) {
					template = stg.getInstanceOf("scalar_subtract_vector");
				}
			} else if (type == SymbolTable.tMATRIX) {
				if (lhs_type == SymbolTable.tMATRIX &&
						rhs_type == SymbolTable.tMATRIX) {
					template = stg.getInstanceOf("matrix_subtract_matrix");
					insertMatrixSizeCheck = true;
				} else if (lhs_type == SymbolTable.tMATRIX &&
						isNumber(rhs_type)) {
					template = stg.getInstanceOf("matrix_subtract_scalar");
				} else if (rhs_type == SymbolTable.tMATRIX &&
						isNumber(lhs_type)) {
					template = stg.getInstanceOf("scalar_subtract_matrix"); // Need to know what side is scalar
				}
			} else {
				if (lhs_type ==SymbolTable.tINTEGER) {
					template = stg.getInstanceOf("int_sub");
				} else if (lhs_type ==SymbolTable.tREAL) {
					template = stg.getInstanceOf("real_sub");
				} 
			}
			break;
		case MULT:
			if (type == SymbolTable.tINTERVAL) {
				if (lhs_type == SymbolTable.tINTERVAL &&
						rhs_type == SymbolTable.tINTERVAL) {
					template = stg.getInstanceOf("interval_multiply_interval");
				}
			} else if (type == SymbolTable.tVECTOR) {
				if (lhs_type == SymbolTable.tVECTOR &&
						rhs_type == SymbolTable.tVECTOR) {
					template = stg.getInstanceOf("vector_multiply_vector");
					insertVectorSizeCheck = true;
				} else if (lhs_type == SymbolTable.tVECTOR &&
						isNumber(rhs_type)) {
					template = stg.getInstanceOf("vector_multiply_scalar");
				} else if (rhs_type == SymbolTable.tVECTOR &&
						isNumber(lhs_type)) {
					template = stg.getInstanceOf("scalar_multiply_vector"); // Need to know what side is scalar
				}
			} else if (type == SymbolTable.tMATRIX) {
				if (lhs_type == SymbolTable.tMATRIX &&
						rhs_type == SymbolTable.tMATRIX) {
					template = stg.getInstanceOf("matrix_multiply_matrix");
					insertMatrixSizeCheck = true;
				} else if (lhs_type == SymbolTable.tMATRIX &&
						isNumber(rhs_type)) {
					template = stg.getInstanceOf("matrix_multiply_scalar");
				} else if (rhs_type == SymbolTable.tMATRIX &&
						isNumber(lhs_type)) {
					template = stg.getInstanceOf("scalar_multiply_matrix"); // Need to know what side is scalar
				}
			} else {
				if (lhs_type ==SymbolTable.tINTEGER) {
					template = stg.getInstanceOf("int_mul");
				} else if (lhs_type ==SymbolTable.tREAL) {
					template = stg.getInstanceOf("real_mul");
				}
			}
			break;
		case DIV:
			if (type == SymbolTable.tINTERVAL) {
				if (lhs_type == SymbolTable.tINTERVAL &&
						rhs_type == SymbolTable.tINTERVAL) {
					template = stg.getInstanceOf("interval_divide_interval");
				}
			} else if (type == SymbolTable.tVECTOR) {
				if (lhs_type == SymbolTable.tVECTOR &&
						rhs_type == SymbolTable.tVECTOR) {
					template = stg.getInstanceOf("vector_divide_vector");
					insertVectorSizeCheck = true;
				} else if (lhs_type == SymbolTable.tVECTOR &&
						isNumber(rhs_type)) {
					template = stg.getInstanceOf("vector_divide_scalar");
				}  else if (rhs_type == SymbolTable.tVECTOR &&
						isNumber(lhs_type)) {
					template = stg.getInstanceOf("scalar_divide_vector");
				}
			} else if (type == SymbolTable.tMATRIX) {
				if (lhs_type == SymbolTable.tMATRIX &&
						rhs_type == SymbolTable.tMATRIX) {
					template = stg.getInstanceOf("matrix_divide_matrix");
					insertMatrixSizeCheck = true;
				} else if (lhs_type == SymbolTable.tMATRIX &&
						isNumber(rhs_type)) {
					template = stg.getInstanceOf("matrix_divide_scalar");
				} else if (rhs_type == SymbolTable.tMATRIX &&
						isNumber(lhs_type)) {
					template = stg.getInstanceOf("scalar_divide_matrix"); // Need to know what side is scalar
				}
			} else {
				if (lhs_type ==SymbolTable.tINTEGER) {
					template = stg.getInstanceOf("int_div");
				} else if (lhs_type ==SymbolTable.tREAL) {
					template = stg.getInstanceOf("real_div");
				}
			}
			break;
		case MOD:
			if (type == SymbolTable.tVECTOR) {
				if (lhs_type == SymbolTable.tVECTOR &&
						rhs_type == SymbolTable.tVECTOR) {
					template = stg.getInstanceOf("vector_modulus_vector");
					insertVectorSizeCheck = true;
				} else if (lhs_type == SymbolTable.tVECTOR &&
						isNumber(rhs_type)) {
					template = stg.getInstanceOf("vector_modulus_scalar");
				} else if (rhs_type == SymbolTable.tVECTOR &&
						isNumber(lhs_type)) {
					template = stg.getInstanceOf("scalar_modulus_vector"); // Need to know what side is scalar
				}
			} else if (type == SymbolTable.tMATRIX) {
				if (lhs_type == SymbolTable.tMATRIX &&
						rhs_type == SymbolTable.tMATRIX) {
					template = stg.getInstanceOf("matrix_modulus_matrix");
					insertMatrixSizeCheck = true;
				} else if (lhs_type == SymbolTable.tMATRIX &&
						isNumber(rhs_type)) {
					template = stg.getInstanceOf("matrix_modulus_scalar");
				} else if (rhs_type == SymbolTable.tMATRIX &&
						isNumber(lhs_type)) {
					template = stg.getInstanceOf("scalar_modulus_matrix"); // Need to know what side is scalar
				}
			} else {
				if (lhs_type ==SymbolTable.tINTEGER) {
					template = stg.getInstanceOf("int_mod");
				} else if (lhs_type ==SymbolTable.tREAL) {
					template = stg.getInstanceOf("real_mod");
				}
			}
			break;
		case POWER:
			if (type == SymbolTable.tVECTOR) {
				if (lhs_type == SymbolTable.tVECTOR &&
						rhs_type == SymbolTable.tVECTOR) {
					template = stg.getInstanceOf("vector_power_vector");
					insertVectorSizeCheck = true;
				} else if (lhs_type == SymbolTable.tVECTOR &&
						isNumber(rhs_type)) {
					template = stg.getInstanceOf("vector_power_scalar");
				} else if (rhs_type == SymbolTable.tVECTOR &&
						isNumber(lhs_type)) {
					template = stg.getInstanceOf("scalar_power_vector"); // Need to know what side is scalar
				}
			} else if (type == SymbolTable.tMATRIX) {
				if (lhs_type == SymbolTable.tMATRIX &&
						rhs_type == SymbolTable.tMATRIX) {
					template = stg.getInstanceOf("matrix_power_matrix");
					insertMatrixSizeCheck = true;
				} else if (lhs_type == SymbolTable.tMATRIX &&
						isNumber(rhs_type)) {
					template = stg.getInstanceOf("matrix_power_scalar");
				} else if (rhs_type == SymbolTable.tMATRIX &&
						isNumber(lhs_type)) {
					template = stg.getInstanceOf("scalar_power_matrix"); // Need to know what side is scalar
				}
			} else {
				if (lhs_type ==SymbolTable.tINTEGER) {
					template = stg.getInstanceOf("int_pow");
				} else if (lhs_type ==SymbolTable.tREAL) {
					template = stg.getInstanceOf("real_pow");
				}
			}
			break;
		case CONCAT:
			if (type == SymbolTable.tVECTOR) {
				if (lhs_type == SymbolTable.tVECTOR &&
						rhs_type == SymbolTable.tVECTOR) {
					template = stg.getInstanceOf("vector_concat_vector");
				} else if (lhs_type == SymbolTable.tVECTOR &&
						isScalar(rhs_type)) {
					template = stg.getInstanceOf("vector_concat_scalar");
				} else if (rhs_type == SymbolTable.tVECTOR &&
						isScalar(lhs_type)) {
					template = stg.getInstanceOf("scalar_concat_vector"); // Need to know what side is scalar
				}
			} 
			break;	
		case DOTPRODUCT:
			if (lhs_type == SymbolTable.tVECTOR &&
				rhs_type == SymbolTable.tVECTOR) {
				template = stg.getInstanceOf("vector_dot_product");
			} if (lhs_type == SymbolTable.tMATRIX &&
					rhs_type == SymbolTable.tMATRIX) {
					template = stg.getInstanceOf("matrix_dot_product");
				}
			break;
		}
		
		if (elementTypeIndex >= 0) {
			template.setAttribute("type_name", typeIndexToName.get(elementTypeIndex));

			StringTemplate llvmType = stg.getInstanceOf(typeIndexToName.get(elementTypeIndex) + "_type");
			template.setAttribute("llvm_type", llvmType);
		}

		template.setAttribute("rhs_id", rhs_id);
		template.setAttribute("rhs", rhs);
		template.setAttribute("lhs_id", lhs_id);
		template.setAttribute("lhs", lhs);
		template.setAttribute("id", id);

		if (insertVectorSizeCheck) {
			StringTemplate check = stg.getInstanceOf("check_vectors_same_length");
			check.setAttribute("id", DashAST.getUniqueId());
			check.setAttribute("lhs_id", lhs_id);
			check.setAttribute("rhs_id", rhs_id);
			template.setAttribute("length_check", check);
		} else if (insertMatrixSizeCheck) {
			StringTemplate check = stg.getInstanceOf("check_matrices_same_length");
			check.setAttribute("id", DashAST.getUniqueId());
			check.setAttribute("lhs_id", lhs_id);
			check.setAttribute("rhs_id", rhs_id);
			template.setAttribute("size_check", check);
		}

		return template;
	}
}
