;header
        ; static header
    (set-option :produce-models true)
    (set-logic ALL)
    (declare-fun valueOf_Int (Int) Int)
    (declare-fun hasRole (Int String) Bool)
    (define-sort ABS.StdLib.Int () Int)
    (define-sort ABS.StdLib.Float () Real)
    (define-sort ABS.StdLib.Bool () Bool)
    (define-sort ABS.StdLib.String () String)
    (declare-const Unit Int)
    (assert (= Unit 0))
    (declare-sort UNBOUND 0)
    
(define-sort Field () Int)
    ; end static header
;primitive type declaration
    (declare-sort ABS.StdLib.Rat 0)
	(declare-sort ABS.StdLib.Fut 0)
;valueOf
    (declare-fun   valueOf_ABS_StdLib_Int (ABS.StdLib.Fut) Int)
(declare-fun   valueOf_ABS_StdLib_Bool(ABS.StdLib.Fut) Bool)
;data type declaration
    ; DataTypes declaration
(declare-datatypes  
	((ABS.StdLib.Exception 0) (Interface 0) (Dummy.NewSpec 0) (ABS.StdLib.Unit 0) (ABS.StdLib.Maybe 0) (ABS.StdLib.Either 0) (ABS.StdLib.Pair 0) (ABS.StdLib.Triple 0) (ABS.StdLib.Set 0) (ABS.StdLib.List 0) (ABS.StdLib.Map 0)) 
	(
	((ABS.StdLib.Exceptions.DivisionByZeroException) (ABS.StdLib.Exceptions.AssertionFailException) (ABS.StdLib.Exceptions.PatternMatchFailException) (ABS.StdLib.Exceptions.NullPointerException) (ABS.StdLib.Exceptions.StackOverflowEcxeption) (ABS.StdLib.Exceptions.HeapOverflowException) (ABS.StdLib.Exceptions.KeyboardInterruptException) (ABS.StdLib.Exceptions.ObjectDeadException)) 
	((ABS.StdLib.Object)) 
	((Dummy.Demonic) (Dummy.Prob (Dummy.Prob_0 ABS.StdLib.Rat))) 
	((ABS.StdLib.Unit)) 
	((ABS.StdLib.Nothing) (ABS.StdLib.Just (ABS.StdLib.Just_0 ABS.StdLib.Int))) 
	((ABS.StdLib.Left (ABS.StdLib.Left_0 ABS.StdLib.Int)) (ABS.StdLib.Right (ABS.StdLib.Right_0 ABS.StdLib.Int))) 
	((ABS.StdLib.Pair (ABS.StdLib.Pair_0 ABS.StdLib.Int) (ABS.StdLib.Pair_1 ABS.StdLib.Int))) 
	((ABS.StdLib.Triple (ABS.StdLib.Triple_0 ABS.StdLib.Int) (ABS.StdLib.Triple_1 ABS.StdLib.Int) (ABS.StdLib.Triple_2 ABS.StdLib.Int))) 
	((ABS.StdLib.EmptySet) (ABS.StdLib.Insert (ABS.StdLib.Insert_0 ABS.StdLib.Int) (ABS.StdLib.Insert_1 ABS.StdLib.Set))) 
	((ABS.StdLib.Nil) (ABS.StdLib.Cons (ABS.StdLib.Cons_0 ABS.StdLib.Int) (ABS.StdLib.Cons_1 ABS.StdLib.List))) 
	((ABS.StdLib.EmptyMap) (ABS.StdLib.InsertAssoc (ABS.StdLib.InsertAssoc_0 ABS.StdLib.Pair) (ABS.StdLib.InsertAssoc_1 ABS.StdLib.Map)))))
(declare-fun   valueOf_Dummy_NewSpec (ABS.StdLib.Fut) Dummy.NewSpec)
(declare-fun   valueOf_ABS_StdLib_Unit (ABS.StdLib.Fut) ABS.StdLib.Unit)
(declare-fun   valueOf_ABS_StdLib_Maybe (ABS.StdLib.Fut) ABS.StdLib.Maybe)
(declare-fun   valueOf_ABS_StdLib_Either (ABS.StdLib.Fut) ABS.StdLib.Either)
(declare-fun   valueOf_ABS_StdLib_Pair (ABS.StdLib.Fut) ABS.StdLib.Pair)
(declare-fun   valueOf_ABS_StdLib_Triple (ABS.StdLib.Fut) ABS.StdLib.Triple)
(declare-fun   valueOf_ABS_StdLib_Set (ABS.StdLib.Fut) ABS.StdLib.Set)
(declare-fun   valueOf_ABS_StdLib_List (ABS.StdLib.Fut) ABS.StdLib.List)
(declare-fun   valueOf_ABS_StdLib_Map (ABS.StdLib.Fut) ABS.StdLib.Map)


;interface type declaration
    (declare-fun   implements (ABS.StdLib.Int Interface) Bool)
    (declare-fun   extends (Interface Interface) Bool)
    (assert (forall ((i1 Interface) (i2 Interface) (i3 Interface))
     (=> (and (extends i1 i2) (extends i2 i3))
      (extends i1 i3))))
      
    (assert (forall ((i1 Interface) (i2 Interface) (object ABS.StdLib.Int))
     (=> (and (extends i1 i2) (implements object i1))
      (implements object i2))))
      
      
      
;generics declaration
    
;heaps declaration
    
; ABS.StdLib.Int Heap declaration
(define-sort Heap_ABS_StdLib_Int () (Array Field ABS.StdLib.Int))
(declare-const heap_ABS_StdLib_Int Heap_ABS_StdLib_Int)
(declare-const old_ABS_StdLib_Int Heap_ABS_StdLib_Int)
(declare-const last_ABS_StdLib_Int Heap_ABS_StdLib_Int)
(declare-fun anon_ABS_StdLib_Int (Heap_ABS_StdLib_Int) Heap_ABS_StdLib_Int)
; ABS.StdLib.Float Heap declaration
(define-sort Heap_ABS_StdLib_Float () (Array Field ABS.StdLib.Float))
(declare-const heap_ABS_StdLib_Float Heap_ABS_StdLib_Float)
(declare-const old_ABS_StdLib_Float Heap_ABS_StdLib_Float)
(declare-const last_ABS_StdLib_Float Heap_ABS_StdLib_Float)
(declare-fun anon_ABS_StdLib_Float (Heap_ABS_StdLib_Float) Heap_ABS_StdLib_Float)
; Dummy.NewSpec Heap declaration
(define-sort Heap_Dummy_NewSpec () (Array Field Dummy.NewSpec))
(declare-const heap_Dummy_NewSpec Heap_Dummy_NewSpec)
(declare-const old_Dummy_NewSpec Heap_Dummy_NewSpec)
(declare-const last_Dummy_NewSpec Heap_Dummy_NewSpec)
(declare-fun anon_Dummy_NewSpec (Heap_Dummy_NewSpec) Heap_Dummy_NewSpec)
; ABS.StdLib.Unit Heap declaration
(define-sort Heap_ABS_StdLib_Unit () (Array Field ABS.StdLib.Unit))
(declare-const heap_ABS_StdLib_Unit Heap_ABS_StdLib_Unit)
(declare-const old_ABS_StdLib_Unit Heap_ABS_StdLib_Unit)
(declare-const last_ABS_StdLib_Unit Heap_ABS_StdLib_Unit)
(declare-fun anon_ABS_StdLib_Unit (Heap_ABS_StdLib_Unit) Heap_ABS_StdLib_Unit)
; ABS.StdLib.String Heap declaration
(define-sort Heap_ABS_StdLib_String () (Array Field ABS.StdLib.String))
(declare-const heap_ABS_StdLib_String Heap_ABS_StdLib_String)
(declare-const old_ABS_StdLib_String Heap_ABS_StdLib_String)
(declare-const last_ABS_StdLib_String Heap_ABS_StdLib_String)
(declare-fun anon_ABS_StdLib_String (Heap_ABS_StdLib_String) Heap_ABS_StdLib_String)
; ABS.StdLib.Rat Heap declaration
(define-sort Heap_ABS_StdLib_Rat () (Array Field ABS.StdLib.Rat))
(declare-const heap_ABS_StdLib_Rat Heap_ABS_StdLib_Rat)
(declare-const old_ABS_StdLib_Rat Heap_ABS_StdLib_Rat)
(declare-const last_ABS_StdLib_Rat Heap_ABS_StdLib_Rat)
(declare-fun anon_ABS_StdLib_Rat (Heap_ABS_StdLib_Rat) Heap_ABS_StdLib_Rat)
; ABS.StdLib.Bool Heap declaration
(define-sort Heap_ABS_StdLib_Bool () (Array Field ABS.StdLib.Bool))
(declare-const heap_ABS_StdLib_Bool Heap_ABS_StdLib_Bool)
(declare-const old_ABS_StdLib_Bool Heap_ABS_StdLib_Bool)
(declare-const last_ABS_StdLib_Bool Heap_ABS_StdLib_Bool)
(declare-fun anon_ABS_StdLib_Bool (Heap_ABS_StdLib_Bool) Heap_ABS_StdLib_Bool)
; ABS.StdLib.Fut Heap declaration
(define-sort Heap_ABS_StdLib_Fut () (Array Field ABS.StdLib.Fut))
(declare-const heap_ABS_StdLib_Fut Heap_ABS_StdLib_Fut)
(declare-const old_ABS_StdLib_Fut Heap_ABS_StdLib_Fut)
(declare-const last_ABS_StdLib_Fut Heap_ABS_StdLib_Fut)
(declare-fun anon_ABS_StdLib_Fut (Heap_ABS_StdLib_Fut) Heap_ABS_StdLib_Fut)
; ABS.StdLib.Maybe Heap declaration
(define-sort Heap_ABS_StdLib_Maybe () (Array Field ABS.StdLib.Maybe))
(declare-const heap_ABS_StdLib_Maybe Heap_ABS_StdLib_Maybe)
(declare-const old_ABS_StdLib_Maybe Heap_ABS_StdLib_Maybe)
(declare-const last_ABS_StdLib_Maybe Heap_ABS_StdLib_Maybe)
(declare-fun anon_ABS_StdLib_Maybe (Heap_ABS_StdLib_Maybe) Heap_ABS_StdLib_Maybe)
; ABS.StdLib.Either Heap declaration
(define-sort Heap_ABS_StdLib_Either () (Array Field ABS.StdLib.Either))
(declare-const heap_ABS_StdLib_Either Heap_ABS_StdLib_Either)
(declare-const old_ABS_StdLib_Either Heap_ABS_StdLib_Either)
(declare-const last_ABS_StdLib_Either Heap_ABS_StdLib_Either)
(declare-fun anon_ABS_StdLib_Either (Heap_ABS_StdLib_Either) Heap_ABS_StdLib_Either)
; ABS.StdLib.Pair Heap declaration
(define-sort Heap_ABS_StdLib_Pair () (Array Field ABS.StdLib.Pair))
(declare-const heap_ABS_StdLib_Pair Heap_ABS_StdLib_Pair)
(declare-const old_ABS_StdLib_Pair Heap_ABS_StdLib_Pair)
(declare-const last_ABS_StdLib_Pair Heap_ABS_StdLib_Pair)
(declare-fun anon_ABS_StdLib_Pair (Heap_ABS_StdLib_Pair) Heap_ABS_StdLib_Pair)
; ABS.StdLib.Triple Heap declaration
(define-sort Heap_ABS_StdLib_Triple () (Array Field ABS.StdLib.Triple))
(declare-const heap_ABS_StdLib_Triple Heap_ABS_StdLib_Triple)
(declare-const old_ABS_StdLib_Triple Heap_ABS_StdLib_Triple)
(declare-const last_ABS_StdLib_Triple Heap_ABS_StdLib_Triple)
(declare-fun anon_ABS_StdLib_Triple (Heap_ABS_StdLib_Triple) Heap_ABS_StdLib_Triple)
; ABS.StdLib.Set Heap declaration
(define-sort Heap_ABS_StdLib_Set () (Array Field ABS.StdLib.Set))
(declare-const heap_ABS_StdLib_Set Heap_ABS_StdLib_Set)
(declare-const old_ABS_StdLib_Set Heap_ABS_StdLib_Set)
(declare-const last_ABS_StdLib_Set Heap_ABS_StdLib_Set)
(declare-fun anon_ABS_StdLib_Set (Heap_ABS_StdLib_Set) Heap_ABS_StdLib_Set)
; ABS.StdLib.List Heap declaration
(define-sort Heap_ABS_StdLib_List () (Array Field ABS.StdLib.List))
(declare-const heap_ABS_StdLib_List Heap_ABS_StdLib_List)
(declare-const old_ABS_StdLib_List Heap_ABS_StdLib_List)
(declare-const last_ABS_StdLib_List Heap_ABS_StdLib_List)
(declare-fun anon_ABS_StdLib_List (Heap_ABS_StdLib_List) Heap_ABS_StdLib_List)
; ABS.StdLib.Map Heap declaration
(define-sort Heap_ABS_StdLib_Map () (Array Field ABS.StdLib.Map))
(declare-const heap_ABS_StdLib_Map Heap_ABS_StdLib_Map)
(declare-const old_ABS_StdLib_Map Heap_ABS_StdLib_Map)
(declare-const last_ABS_StdLib_Map Heap_ABS_StdLib_Map)
(declare-fun anon_ABS_StdLib_Map (Heap_ABS_StdLib_Map) Heap_ABS_StdLib_Map)
;wildcards declaration
    
    
; parametric functions decl
    ; no parametric declarations
;functions declaration
    
;generic functions declaration :to be implemented and added
;    
;fields declaration
    
;variables declaration
    
;objects declaration
    
    
;objects interface declaration
    
;funcs declaration
    
;fields constraints
    
    ; Precondition
    (assert true )
    ; Negated postcondition
    (assert (not (and (and (and (and (and (<=  0 0) (<=  0 10)) (<=  0 0)) (<  0 10)) (<=  0 0)) (<  0 10)))) 
    (check-sat)
    
    (exit)