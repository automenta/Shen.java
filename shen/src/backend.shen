
\*                                                   

**********************************************************************************
*                           The License						*
* 										*
* The user is free to produce commercial applications with the software, to 	*
* distribute these applications in source or binary  form, and to charge monies *
* for them as he sees fit and in concordance with the laws of the land subject 	*
* to the following license.							*
*										* 
* 1. The license applies to all the software and all derived software and 	*
*    must appear on such.							*
*										*
* 2. It is illegal to distribute the software without this license attached	*
*    to it and use of the software implies agreement with the license as such.  *
*    It is illegal for anyone who is not the copyright holder to tamper with 	*
*    or change the license.							*
*										*
* 3. Neither the names of Lambda Associates or the copyright holder may be used *
*    to endorse or promote products built using the software without specific 	*
*    prior written permission from the copyright holder.			*
*										*
* 4. That possession of this license does not confer on the copyright holder 	*
*    any special contractual obligation towards the user. That in no event 	* 
*    shall the copyright holder be liable for any direct, indirect, incidental, *   
*    special, exemplary or consequential damages (including but not limited     *
*    to procurement of substitute goods or services, loss of use, data, 	* 
*    interruption), however caused and on any theory of liability, whether in	* 
*    contract, strict liability or tort (including negligence) arising in any 	*
*    way out of the use of the software, even if advised of the possibility of 	*
*    such damage.								* 
*										*
* 5. It is permitted for the user to change the software, for the purpose of 	*
*    improving performance, correcting an error, or porting to a new platform, 	*
*    and distribute the derived version of Shen provided the resulting program 	*
*    conforms in all respects to the Shen standard and is issued under that     * 
*    title. The user must make it clear with his distribution that he/she is 	*
*    the author of the changes and what these changes are and why. 		*
*										*
* 6. Derived versions of this software in whatever form are subject to the same *
*    restrictions. In particular it is not permitted to make derived copies of  *
*    this software which do not conform to the Shen standard or appear under a  *
*    different title.								*
*										*
*    It is permitted to distribute versions of Shen which incorporate libraries,*
*    graphics or other facilities which are not part of the Shen standard.	*
*										*
* For an explication of this license see www.shenlanguage.org/license.htm which *
* explains this license in full. 
*				 						*
*********************************************************************************

*\

(package shen []

(define kl-to-lisp
   Params Param -> Param    where (element? Param Params)
   Params [type X _] -> (kl-to-lisp Params X)
   Params [lambda X Y] 
     -> (protect [FUNCTION [LAMBDA [X] (kl-to-lisp [X | Params] Y)]]) 
   Params [let X Y Z] -> (protect [LET [[X (kl-to-lisp Params Y)]] 
                                       (kl-to-lisp [X | Params] Z)])
   _ [defun F Params Code] -> (protect [DEFUN F Params (kl-to-lisp Params Code)])
   Params [cond | Cond] -> (protect [COND | (map (/. C (cond_code Params C)) Cond)])  
   Params [F | X] -> (let Arguments (map (/. Y (kl-to-lisp Params Y)) X)
                          (optimise-application
                            (cases (element? F Params) 
                                   [apply F [(protect LIST) | Arguments]]
                                   (cons? F) [apply (kl-to-lisp Params F) 
                                                   [(protect LIST) | Arguments]]
                                   (partial-application? F Arguments) 
                                   (partially-apply F Arguments)
                                   true [(maplispsym F) | Arguments])))
   _ N -> N      where (number? N)
   _ S -> S      where (string? S)                                
   _ X -> (protect [QUOTE X]))  
   
(define apply
  F Arguments -> (trap-error ((protect APPLY) F Arguments) 
                             (/. E (analyse-application F Arguments))))

\\ Very slow if higher-order partial application is used; but accurate.                             
(define analyse-application
  F Arguments -> (let Lambda (if (partial-application? F Arguments)
                                 ((protect EVAL) (mk-lambda F (arity F)))
                                 F)
                      (curried-apply F Arguments))) 
                      
(define curried-apply
  F [] -> F
  F [X | Y] -> (curried-apply (F X) Y))                      
                                
(define partial-application?
  F Arguments -> (let Arity (trap-error (arity F) (/. E -1))
                      (cases (= Arity -1) false
                             (= Arity (length Arguments)) false
                             (> (length Arguments) Arity) false
                             true true)))
                      
(define partially-apply
  F Arguments -> (let Arity (arity F)                   
                      Lambda (mk-lambda [(maplispsym F)] Arity)
                      (build-partial-application Lambda Arguments)))

(define optimise-application
   [hd X] -> (protect [CAR X])
   [tl X] -> (protect [CDR X])
   [cons X Y] -> (protect [CONS X Y])
   [append X Y] -> (protect [APPEND X Y])
   [reverse X] -> (protect [REVERSE X])
   [if P Q R] -> (protect [IF (wrap P) Q R])
   [+ 1 X] -> [1+ X]
   [+ X 1] -> [1+ X]
   [- X 1] -> [1- X]
   [value [Quote X]] -> X  	       where (= Quote (protect QUOTE))
   [set [Quote X] [1+ X]] -> [(protect INCF) X]  where (= Quote (protect QUOTE))
   [set [Quote X] [1- X]] -> [(protect DECF) X]  where (= Quote (protect QUOTE))
   X -> X)
                      
(define mk-lambda
  F 0 -> F
  F N -> (let X (gensym (protect V))
                [lambda X (mk-lambda (append F [X]) (- N 1))]))
    
(define build-partial-application
  F [] -> F
  F [Argument | Arguments] 
  -> (build-partial-application [(protect FUNCALL) F Argument] Arguments))

(define cond_code
   Params [Test Result] -> [(lisp_test Params Test) 
                             (kl-to-lisp Params Result)])
                             
(define lisp_test
   _ true -> (protect T)
   Params [and | Tests] 
   -> [(protect AND) | (map (/. X (wrap (kl-to-lisp Params X))) Tests)]
   Params Test -> (wrap (kl-to-lisp Params Test))) 
   
 (define wrap 
    [cons? X] -> [(protect CONSP) X]
    [string? X] -> [(protect STRINGP) X]
    [number? X] -> [(protect NUMBERP) X]
    [empty? X] -> [(protect NULL) X]
    [and P Q] -> [(protect AND) (wrap P) (wrap Q)]
    [or P Q] -> [(protect OR) (wrap P) (wrap Q)] 
    [not P] -> [(protect NOT) (wrap P)] 
    [equal? X []] -> [(protect NULL) X]
    [equal? [] X] -> [(protect NULL) X]
    [equal? X [Quote Y]] -> [(protect EQ) X [Quote Y]]    
        where (and (= ((protect SYMBOLP) Y) (protect T)) (= Quote (protect QUOTE)))
    [equal? [Quote Y] X] -> [(protect EQ) [Quote Y] X]    
        where (and (= ((protect SYMBOLP) Y) (protect T)) (= Quote (protect QUOTE))) 
    [equal? [fail] X] -> [(protect EQ) [fail] X]
    [equal? X [fail]] -> [(protect EQ) X [fail]]
    [equal? S X] -> [(protect EQUAL) S X]  where (string? S)
    [equal? X S] -> [(protect EQUAL) X S]  where (string? S)
    [equal? X Y] -> [ABSEQUAL X Y]
    [greater? X Y] -> [> X Y]
    [greater-than-or-equal-to? X Y] -> [>= X Y]
    [less? X Y] -> [< X Y]
    [less-than-or-equal-to? X Y] -> [<= X Y]
    X -> [wrapper X])

 (define wrapper
   true -> (protect T)
   false -> []
   X -> (error "boolean expected: not ~S~%" X)) 
        
(define maplispsym  
    = -> equal?
    > -> greater?
    < -> less?
    >= -> greater-than-or-equal-to?
    <= -> less-than-or-equal-to?
    + -> add
    - -> subtract
    / -> divide
    * -> multiply
    F -> F)
   
    )