if exists("b:current_syntax")
  finish
endif

syn case ignore

syn match  gtdDir	"^[^=]*$"
syn match  gtdName "^.*="me=e-1
syn match  gtdComment	"^#.*"
syn match  gtdKey "=.*$"ms=s+1

hi def link gtdComment	Special
hi def link gtdDir	Statement
hi def link gtdName	Type
hi def link gtdKey      Identifier

set tabstop=3

let b:current_syntax = "tree"
