NAVODILA ZA UPORABO:

1) prijavis se z uporabniskim imenom (1-17 znakov, brez presledkov)
2) po defaultu je "navadno" sporocilo javno (torej je namenjeno vsem), 
   taka sporočila imajo na clientu predpono [RKchat]
3) zasebno sporocilo napišeš kot >> @uporabnisko_ime_prejemnika + presledek + sporocilo <<
   (brez + in >><< seveda)
   

UPORABLJEN FORMAT
┌───────────────────────────────────────────────────────────────────────────────────────┐
│ 1B Tip  │ 8B Datum   │ 8B Čas     │ 17B Pošiljatelj   │ 17B Prejemnik     │ Sporočilo │
│         │ (ddMMyyyy) │ (HHmmss)   │ (padding == ' ')  │ (padding == ' ')  │ (…∞…)     │
└───────────────────────────────────────────────────────────────────────────────────────┘

TIPI SPOROČILA

Tip 1 == LOGIN
Tip 2 == Javno sporočilo
Tip 3 == Zasebno sporočilo 
Tip 4 == Tip