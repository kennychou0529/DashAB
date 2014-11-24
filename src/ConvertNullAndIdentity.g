tree grammar ConvertNullAndIdentity;

options {
  language = Java;
  tokenVocab = Dash;
  ASTLabelType = DashAST;
  filter = true;
  output = AST;
}

@header {
  package ab.dash;
  import ab.dash.DashLexer;
  import ab.dash.ast.*;
}

@members {
    
  SymbolTable symtab;

  public ConvertNullAndIdentity(TreeNodeStream input, SymbolTable symtab) {
    this(input);
    this.symtab = symtab;
    setTreeAdaptor(DashAST.dashAdaptor);
  } 
}

bottomup
  : identExpr
	|	nullExpr
	;
	

nullExpr
  : ^(node = EXPR Null)
  { 
    $node.evalType = $node.promoteToType;
    $node.promoteToType = null;
    
    if ($node.evalType == null)
      $node.evalType = ((DashAST)$node.getChild(0)).promoteToType;
      
    DashAST expr = SymbolTable.getExprForNull($node.evalType);
    if (expr != null) {
      ((DashAST)expr.getChild(0)).evalType = $node.evalType;
      //((DashAST)expr.getChild(0)).promoteToType = null;
      $node.deleteChild(0);
      $node.addChild(expr.getChild(0));
    }
  } 
  ;
  
identExpr
  : ^(node = EXPR Identity)
  { 
    $node.evalType = $node.promoteToType;
    $node.promoteToType = null;
    
    if ($node.evalType == null)
      $node.evalType = ((DashAST)$node.getChild(0)).promoteToType;
      
    DashAST expr = SymbolTable.getExprForIdentity($node.evalType);
    if (expr != null) {
      ((DashAST)expr.getChild(0)).evalType = $node.evalType;
      //((DashAST)expr.getChild(0)).promoteToType = null;
      $node.deleteChild(0);
      $node.addChild(expr.getChild(0));
    }
  } 
  ;