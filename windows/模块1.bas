Attribute VB_Name = "ģ��1"
Sub m()
For Each sh In Sheets
k = k + 1
Cells(k, 1) = sh.Name
Next
End Sub
