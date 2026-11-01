from PIL import Image
names=['v0e-preview-dark','v0e-preview-light','v0e-320dp','v0e-font130']
for n in names:
    im=Image.open(f'docs/ui-v0/{n}.png').convert('RGB')
    im.crop((0,840,im.width,1300)).save(f'docs/ui-v0/_{n}-c.png')
print('ok')
