from PIL import Image, ImageChops
a=Image.open('docs/ui-v0/v0e-preview-dark.png').convert('RGB')
b=Image.open('docs/ui-v0/_test.png').convert('RGB')
d=ImageChops.difference(a,b)
print('same size', a.size==b.size, 'bbox', d.getbbox())
